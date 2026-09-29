package com.sih.nivara.device.dto.mapper;

import com.sih.nivara.device.dto.response.PatientDeviceResponse;
import com.sih.nivara.device.entity.PatientDevice;
import com.sih.nivara.dto.response.PatientSelfResponse;
import com.sih.nivara.entity.Patient;

import java.time.Instant;

/**
 * Converts devices and a patient's own record to responses. Reads the device's patient and
 * creating account, which are lazy, so the device must have been loaded with them fetched.
 */
public final class PatientDeviceMapper {

    private PatientDeviceMapper() {
        // utility class
    }

    public static PatientDeviceResponse toResponse(PatientDevice device, Instant now) {
        return new PatientDeviceResponse(
                device.getUuid(),
                device.getPatient().getUuid(),
                device.getLabel(),
                device.statusAt(now),
                device.getCreatedByUser().getFullName(),
                device.getCreatedAt(),
                device.getPairingExpiresAt(),
                device.getPairedAt(),
                device.getLastUsedAt(),
                device.getRevokedAt());
    }

    public static PatientSelfResponse toSelfResponse(Patient patient) {
        return new PatientSelfResponse(
                patient.getUuid(),
                patient.getFullName(),
                patient.getPreferredName(),
                patient.getPreferredLanguage(),
                patient.getTimezone());
    }
}
