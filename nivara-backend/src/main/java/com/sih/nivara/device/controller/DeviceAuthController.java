package com.sih.nivara.device.controller;

import com.sih.nivara.device.dto.mapper.PatientDeviceMapper;
import com.sih.nivara.device.dto.request.DeviceTokenRequest;
import com.sih.nivara.device.dto.request.PairDeviceRequest;
import com.sih.nivara.device.dto.response.PairedDeviceResponse;
import com.sih.nivara.device.service.PatientDeviceService;
import com.sih.nivara.dto.mapper.AccountMapper;
import com.sih.nivara.dto.response.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * How a patient's device signs in. Both endpoints are public, like /api/auth/login: the pairing
 * code or the device secret is the credential.
 *
 * <p>A device pairs once with the code a caregiver created, and then signs in with its uuid and
 * secret whenever its access token expires. The tokens are ordinary access tokens for the
 * patient's own account, bound to the device, and reach only patient endpoints.
 */
@RestController
@RequestMapping("/api/auth/device")
public class DeviceAuthController {

    private final PatientDeviceService deviceService;

    public DeviceAuthController(PatientDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Redeems a pairing code. 200 with the device secret, shown this once, and a first access
     * token; 400 for any unusable code.
     */
    @PostMapping("/pair")
    public PairedDeviceResponse pair(@Valid @RequestBody PairDeviceRequest request) {
        PatientDeviceService.Pairing pairing;
        try {
            pairing = deviceService.pair(request.code());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Two devices redeemed the same code at the same moment; the other one won.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PatientDeviceService.INVALID_CODE);
        }
        return new PairedDeviceResponse(
                pairing.device().getUuid(),
                pairing.deviceSecret(),
                PatientDeviceMapper.toSelfResponse(pairing.device().getPatient()),
                AccountMapper.toTokenResponse(pairing.token().value(), pairing.token().expiresAt(), pairing.account()));
    }

    /** Signs a paired device in again. 200 with a new access token; 401 for any failure. */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody DeviceTokenRequest request) {
        PatientDeviceService.DeviceToken issued = deviceService.issueToken(request.deviceUuid(), request.deviceSecret());
        return AccountMapper.toTokenResponse(issued.token().value(), issued.token().expiresAt(), issued.account());
    }
}
