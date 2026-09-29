package com.sih.nivara.device.dto.response;

import com.sih.nivara.device.entity.PatientDevice;

import java.time.Instant;
import java.util.UUID;

/**
 * A patient's device as caregivers see it. Never includes the code or the secret, not even
 * hashed. pairingExpiresAt is set only while the device waits to be paired.
 */
public record PatientDeviceResponse(
        UUID uuid,
        UUID patientUuid,
        String label,
        PatientDevice.Status status,
        String createdByName,
        Instant createdAt,
        Instant pairingExpiresAt,
        Instant pairedAt,
        Instant lastUsedAt,
        Instant revokedAt) {
}
