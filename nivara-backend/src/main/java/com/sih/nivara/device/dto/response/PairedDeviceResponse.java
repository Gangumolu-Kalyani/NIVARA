package com.sih.nivara.device.dto.response;

import com.sih.nivara.dto.response.PatientSelfResponse;
import com.sih.nivara.dto.response.TokenResponse;

import java.util.UUID;

/**
 * A freshly paired device. deviceSecret is shown this once and must be kept by the device: with
 * deviceUuid it signs the device in again through /api/auth/device/token. token is a first access
 * token, so the device can start at once.
 */
public record PairedDeviceResponse(
        UUID deviceUuid,
        String deviceSecret,
        PatientSelfResponse patient,
        TokenResponse token) {
}
