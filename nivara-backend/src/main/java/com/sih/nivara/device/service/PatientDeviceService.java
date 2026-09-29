package com.sih.nivara.device.service;

import com.sih.nivara.device.entity.PatientDevice;
import com.sih.nivara.device.repository.PatientDeviceRepository;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.security.JwtTokenService;
import com.sih.nivara.service.PatientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pairs patient devices and signs them in.
 *
 * <ol>
 *   <li>A caregiver with EDITOR access creates a one-time pairing code for the patient
 *       ({@link #createPairingCode}); the controller checks that access.</li>
 *   <li>The patient's device redeems it ({@link #pair}) and receives a device secret and a first
 *       access token. The patient's own PATIENT account is created on first pairing.</li>
 *   <li>From then on the device exchanges its uuid and secret for access tokens
 *       ({@link #issueToken}).</li>
 * </ol>
 *
 * <p>Every failure to pair or to sign in gives the same answer, so a caller cannot tell a wrong
 * code from an expired, used or revoked one.
 */
@Service
@Transactional(readOnly = true)
public class PatientDeviceService {

    static final Duration PAIRING_CODE_LIFETIME = Duration.ofMinutes(10);

    public static final String INVALID_CODE = "This pairing code is not valid. Ask a caregiver for a new one.";
    static final String INVALID_CREDENTIALS = "This device is not signed in. Ask a caregiver to pair it again.";

    /** A new pairing code; code is the plaintext, formatted, and is never stored. */
    public record PairingCode(PatientDevice device, String code) {
    }

    /** A freshly paired device: its plaintext secret, shown once, and a first access token. */
    public record Pairing(PatientDevice device, String deviceSecret, AppUser account,
                          JwtTokenService.IssuedToken token) {
    }

    /** An access token issued to a paired device, and the account it signs in as. */
    public record DeviceToken(AppUser account, JwtTokenService.IssuedToken token) {
    }

    private final PatientDeviceRepository deviceRepository;
    private final PatientService patientService;
    private final JwtTokenService jwtTokenService;

    public PatientDeviceService(PatientDeviceRepository deviceRepository,
                                PatientService patientService,
                                JwtTokenService jwtTokenService) {
        this.deviceRepository = deviceRepository;
        this.patientService = patientService;
        this.jwtTokenService = jwtTokenService;
    }

    public Optional<PatientDevice> findByUuid(UUID uuid) {
        return deviceRepository.findByUuid(uuid);
    }

    /** The patient's devices, newest first, in every state. */
    public List<PatientDevice> findByPatient(Patient patient) {
        return deviceRepository.findByPatientOrderByCreatedAtDescIdDesc(patient);
    }

    /** Creates a device waiting to be paired, with a new one-time code valid for 10 minutes. */
    @Transactional
    public PairingCode createPairingCode(Patient patient, String label, AppUser createdBy) {
        String code = DeviceCredentials.newPairingCode();
        String trimmedLabel = label == null || label.isBlank() ? null : label.trim();
        PatientDevice device = deviceRepository.save(new PatientDevice(patient, trimmedLabel, createdBy,
                DeviceCredentials.hash(code), Instant.now().plus(PAIRING_CODE_LIFETIME)));
        return new PairingCode(device, DeviceCredentials.format(code));
    }

    /**
     * Redeems a pairing code. 400 when the code is unknown, malformed, expired, already used or
     * revoked, all with the same message.
     */
    @Transactional
    public Pairing pair(String typedCode) {
        Instant now = Instant.now();
        String code = DeviceCredentials.normalize(typedCode);
        PatientDevice device = Optional.ofNullable(code)
                .flatMap(c -> deviceRepository.findByPairingCodeHash(DeviceCredentials.hash(c)))
                .filter(d -> d.statusAt(now) == PatientDevice.Status.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CODE));

        AppUser account = patientService.ensureLoginAccount(device.getPatient());
        if (!account.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CODE);
        }
        String secret = DeviceCredentials.newSecret();
        device.pair(DeviceCredentials.hash(secret), now);
        device.markUsed(now);
        return new Pairing(device, secret, account, jwtTokenService.issueForDevice(account, device.getUuid()));
    }

    /**
     * Exchanges a paired device's uuid and secret for an access token as the patient. 401 when
     * the device is unknown, not yet paired or revoked, the secret is wrong, or the patient's
     * account is disabled, all with the same message.
     */
    @Transactional
    public DeviceToken issueToken(UUID deviceUuid, String secret) {
        Instant now = Instant.now();
        PatientDevice device = Optional.ofNullable(deviceUuid)
                .flatMap(deviceRepository::findByUuid)
                .filter(d -> d.statusAt(now) == PatientDevice.Status.ACTIVE)
                .filter(d -> DeviceCredentials.matches(secret, d.getSecretHash()))
                .orElseThrow(DeviceCredentialsException::new);

        AppUser account = device.getPatient().getUserAccount();
        if (account == null || !account.isEnabled()) {
            throw new DeviceCredentialsException();
        }
        device.markUsed(now);
        return new DeviceToken(account, jwtTokenService.issueForDevice(account, device.getUuid()));
    }

    /**
     * Revokes a device: it can no longer sign in, and tokens it already holds stop working at once.
     * Revoking a device that has not been paired yet cancels its code. Revoking twice is harmless.
     */
    @Transactional
    public PatientDevice revoke(UUID deviceUuid) {
        PatientDevice device = deviceRepository.findByUuid(deviceUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No device with uuid " + deviceUuid));
        device.revoke(Instant.now());
        return device;
    }

    /** 401 for any failed device sign-in, with one message whatever the cause. */
    static final class DeviceCredentialsException extends ResponseStatusException {
        DeviceCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
    }
}
