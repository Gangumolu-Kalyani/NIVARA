package com.sih.nivara.device.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of "pair this device": the pairing code a caregiver created, as typed. Case, spaces and
 * the hyphen do not matter.
 */
public record PairDeviceRequest(

        @NotBlank
        @Size(max = 20)
        String code) {
}
