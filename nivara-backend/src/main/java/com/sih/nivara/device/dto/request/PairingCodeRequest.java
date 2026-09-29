package com.sih.nivara.device.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional body of "create a pairing code": a name for the device, such as "Kitchen tablet".
 * Mirrors ck_patient_devices_label_not_blank and the column length (V6); a blank label is stored
 * as none.
 */
public record PairingCodeRequest(

        @Size(max = 60)
        String label) {
}
