package com.sih.nivara.device.controller;

import com.sih.nivara.device.dto.mapper.PatientDeviceMapper;
import com.sih.nivara.device.dto.request.PairingCodeRequest;
import com.sih.nivara.device.dto.response.PairingCodeResponse;
import com.sih.nivara.device.dto.response.PatientDeviceResponse;
import com.sih.nivara.device.entity.PatientDevice;
import com.sih.nivara.device.service.PatientDeviceService;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.PatientAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caregivers pair, list and revoke a patient's devices.
 *
 * <p>Authorized by {@link PatientAccessService} against patient_caregivers, like every other
 * patient-scoped API: creating a code and revoking need EDITOR access, listing needs VIEWER. A
 * caregiver with no access to the patient gets 404, as if the patient did not exist.
 */
@RestController
@RequestMapping("/api/patients/{patientUuid}/devices")
public class PatientDeviceController {

    private final PatientDeviceService deviceService;
    private final PatientAccessService patientAccessService;

    public PatientDeviceController(PatientDeviceService deviceService, PatientAccessService patientAccessService) {
        this.deviceService = deviceService;
        this.patientAccessService = patientAccessService;
    }

    /** Creates a one-time pairing code, valid for 10 minutes. 201 with the device's Location. */
    @PostMapping("/pairing-code")
    public ResponseEntity<PairingCodeResponse> createPairingCode(
            @PathVariable UUID patientUuid,
            @Valid @RequestBody(required = false) PairingCodeRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
        PatientDeviceService.PairingCode created = deviceService.createPairingCode(
                patient, request == null ? null : request.label(), patientAccessService.requireCaller());
        PatientDevice device = created.device();
        return ResponseEntity.created(URI.create("/api/patients/" + patientUuid + "/devices/" + device.getUuid()))
                .body(new PairingCodeResponse(device.getUuid(), created.code(), device.getPairingExpiresAt()));
    }

    /** The patient's devices in every state, newest first. */
    @GetMapping
    public List<PatientDeviceResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        Instant now = Instant.now();
        return deviceService.findByPatient(patient).stream()
                .map(device -> PatientDeviceMapper.toResponse(device, now))
                .toList();
    }

    /**
     * Revokes a device, or cancels a code not yet redeemed. The device signs out at once. 404 when
     * the device does not belong to this patient.
     */
    @DeleteMapping("/{deviceUuid}")
    public ResponseEntity<Void> revoke(@PathVariable UUID patientUuid, @PathVariable UUID deviceUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
        PatientDevice device = deviceService.findByUuid(deviceUuid)
                .filter(d -> d.getPatient().getId().equals(patient.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No device with uuid " + deviceUuid));
        deviceService.revoke(device.getUuid());
        return ResponseEntity.noContent().build();
    }
}
