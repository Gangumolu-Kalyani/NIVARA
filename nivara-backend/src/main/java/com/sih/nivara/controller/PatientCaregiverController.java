package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PatientCaregiverMapper;
import com.sih.nivara.dto.request.CaregiverAccessUpdateRequest;
import com.sih.nivara.dto.request.CaregiverAssignmentRequest;
import com.sih.nivara.dto.response.PatientCaregiverResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PatientCaregiver;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.PatientCaregiverService;
import com.sih.nivara.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Care-team REST API: who has access to a patient, and at what level.
 *
 * <p>Any caregiver of the patient (VIEWER and above) can see the care team; only an OWNER can grant,
 * change or revoke access. A caller with no link to the patient gets 404, exactly as for any other
 * patient-scoped endpoint.
 *
 * <p>The caregiver in a request, whether the grantee in the body or the one named in the path, is
 * only ever the subject of the change. The acting account is always the one that authenticated
 * the request, so naming another caregiver's uuid can never make the caller act as them.
 *
 * <p>The invariants (at least one OWNER, exactly one primary caregiver) are kept by
 * {@link PatientCaregiverService}.
 */
@RestController
@RequestMapping("/api/patients/{patientUuid}/caregivers")
public class PatientCaregiverController {

    private final PatientAccessService patientAccessService;
    private final PatientCaregiverService patientCaregiverService;
    private final UserService userService;

    public PatientCaregiverController(PatientAccessService patientAccessService,
                                      PatientCaregiverService patientCaregiverService,
                                      UserService userService) {
        this.patientAccessService = patientAccessService;
        this.patientCaregiverService = patientCaregiverService;
        this.userService = userService;
    }

    /** The patient's care team, in the order access was granted; needs VIEWER access. */
    @GetMapping
    public List<PatientCaregiverResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return patientCaregiverService.findByPatient(patient).stream()
                .map(PatientCaregiverMapper::toResponse)
                .toList();
    }

    /**
     * Gives another account access to this patient; needs OWNER access. Answers 201, 404 when the
     * account does not exist or is disabled, or 409 when it already has access or a second primary
     * caregiver is requested. An omitted accessLevel grants EDITOR, the column default.
     */
    @PostMapping
    public ResponseEntity<PatientCaregiverResponse> grant(@PathVariable UUID patientUuid,
                                                          @Valid @RequestBody CaregiverAssignmentRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.OWNER);
        AppUser grantee = userService.findByUuid(request.caregiverUserUuid())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No account with uuid " + request.caregiverUserUuid()));

        PatientCaregiver link = patientCaregiverService.grant(patient, grantee, request);
        URI location = URI.create("/api/patients/" + patientUuid + "/caregivers/" + grantee.getUuid());
        return ResponseEntity.created(location).body(PatientCaregiverMapper.toResponse(link));
    }

    /**
     * Replaces one caregiver's access level, relationship, primary flag and alert setting; needs
     * OWNER access. 404 when that caregiver has no access to the patient; 409 when the change would
     * leave the patient without an OWNER or without a primary caregiver.
     */
    @PutMapping("/{caregiverUuid}")
    public PatientCaregiverResponse update(@PathVariable UUID patientUuid,
                                           @PathVariable UUID caregiverUuid,
                                           @Valid @RequestBody CaregiverAccessUpdateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.OWNER);
        return PatientCaregiverMapper.toResponse(patientCaregiverService.update(patient, caregiverUuid, request));
    }

    /**
     * Removes one caregiver's access; needs OWNER access. Answers 204, 404 when that caregiver has no
     * access, or 409 for the last OWNER or for the primary caregiver.
     */
    @DeleteMapping("/{caregiverUuid}")
    public ResponseEntity<Void> revoke(@PathVariable UUID patientUuid, @PathVariable UUID caregiverUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.OWNER);
        patientCaregiverService.revoke(patient, caregiverUuid);
        return ResponseEntity.noContent().build();
    }
}
