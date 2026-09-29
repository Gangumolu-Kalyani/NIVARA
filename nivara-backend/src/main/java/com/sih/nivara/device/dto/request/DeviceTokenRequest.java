package com.sih.nivara.device.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of "sign this device in": the device uuid and secret it received when it was paired. */
public record DeviceTokenRequest(

        @NotNull
        UUID deviceUuid,

        @NotBlank
        @Size(max = 100)
        String deviceSecret) {
}
