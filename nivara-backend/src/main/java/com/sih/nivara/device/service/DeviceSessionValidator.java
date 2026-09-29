package com.sih.nivara.device.service;

import com.sih.nivara.device.entity.PatientDevice;
import com.sih.nivara.device.repository.PatientDeviceRepository;
import com.sih.nivara.entity.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Decides, on every request, whether a PATIENT token is still usable: the device it was issued
 * to must exist, be paired, not be revoked, and belong to that very account's patient.
 *
 * <p>Kept separate from {@link PatientDeviceService} so that authentication depends only on the
 * repository, not on token issuing.
 */
@Component
public class DeviceSessionValidator {

    private final PatientDeviceRepository deviceRepository;

    public DeviceSessionValidator(PatientDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public boolean isActiveFor(UUID deviceUuid, AppUser account) {
        return deviceRepository.findByUuid(deviceUuid)
                .filter(device -> device.statusAt(Instant.now()) == PatientDevice.Status.ACTIVE)
                .map(device -> device.getPatient().getUserAccount())
                .filter(linked -> linked != null && linked.getUuid().equals(account.getUuid()))
                .isPresent();
    }
}
