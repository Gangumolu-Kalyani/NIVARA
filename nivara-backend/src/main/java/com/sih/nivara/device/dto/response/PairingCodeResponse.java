package com.sih.nivara.device.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A new pairing code, formatted "ABCD-EFGH". This is the only time the code is shown: only its
 * hash is stored.
 */
public record PairingCodeResponse(
        UUID deviceUuid,
        String pairingCode,
        Instant expiresAt) {
}
