package com.sih.nivara.device;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.security.JwtTokenService;
import com.sih.nivara.service.UserService;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patient device pairing, device sign-in, patient identity and the role boundary between the
 * patient and caregiver APIs.
 */
class PatientDeviceIntegrationTest extends EmbeddedPostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    UserService userService;

    /** Creates a pairing code for the patient as this caregiver; answers the response body. */
    private JsonNode pairingCode(String caregiverToken, String patientUuid) {
        return expect(201, "POST", "/api/patients/" + patientUuid + "/devices/pairing-code", caregiverToken,
                "{\"label\":\"Kitchen tablet\"}").body();
    }

    /** Pairs a device with this code; answers the response body. */
    private JsonNode pair(String code) {
        return expect(200, "POST", "/api/auth/device/pair", null, "{\"code\":\"" + code + "\"}").body();
    }

    private static String accessToken(JsonNode paired) {
        return text(paired.get("token"), "accessToken");
    }

    private JsonNode devices(String caregiverToken, String patientUuid) {
        return expect(200, "GET", "/api/patients/" + patientUuid + "/devices", caregiverToken, null).body();
    }

    @Test
    void caregiverPairsADeviceAndThePatientSignsIn() {
        String caregiver = caregiverToken("Ravi Kumar");
        String patient = createPatient(caregiver, "Lakshmi Rao");

        Http created = expect(201, "POST", "/api/patients/" + patient + "/devices/pairing-code", caregiver,
                "{\"label\":\"Kitchen tablet\"}");
        String code = text(created.body(), "pairingCode");
        assertTrue(code.matches("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$"), code);
        assertNotNull(text(created.body(), "expiresAt"));
        assertTrue(created.raw().headers().firstValue("Location").orElseThrow()
                .endsWith("/devices/" + text(created.body(), "deviceUuid")));
        assertEquals("PENDING", text(devices(caregiver, patient).get(0), "status"));

        // Typed in lower case without the hyphen, as a person might
        JsonNode paired = pair(code.replace("-", "").toLowerCase());
        assertEquals(patient, text(paired.get("patient"), "uuid"));
        assertEquals("Lakshmi Rao", text(paired.get("patient"), "fullName"));
        assertNotNull(text(paired, "deviceSecret"));
        JsonNode account = paired.get("token").get("account");
        assertEquals("PATIENT", text(account, "role"));
        assertNull(text(account, "email"));

        String token = accessToken(paired);
        assertEquals(patient, text(expect(200, "GET", "/api/me/patient", token, null).body(), "uuid"));
        assertEquals("PATIENT", text(expect(200, "GET", "/api/auth/me", token, null).body(), "role"));

        JsonNode device = devices(caregiver, patient).get(0);
        assertEquals("ACTIVE", text(device, "status"));
        assertEquals("Kitchen tablet", text(device, "label"));
        assertEquals("Ravi Kumar", text(device, "createdByName"));
        assertNotNull(text(device, "pairedAt"));
        assertNull(text(device, "pairingExpiresAt"));
        assertNull(device.get("deviceSecret"), "the secret is never listed");

        // A code works once
        expect(400, "POST", "/api/auth/device/pair", null, "{\"code\":\"" + code + "\"}");

        // The device signs in again with its uuid and secret
        String deviceUuid = text(paired, "deviceUuid");
        String secret = text(paired, "deviceSecret");
        JsonNode renewed = expect(200, "POST", "/api/auth/device/token", null,
                "{\"deviceUuid\":\"" + deviceUuid + "\",\"deviceSecret\":\"" + secret + "\"}").body();
        assertEquals("PATIENT", text(renewed.get("account"), "role"));
        assertEquals(patient, text(expect(200, "GET", "/api/me/patient", text(renewed, "accessToken"), null).body(), "uuid"));

        expect(401, "POST", "/api/auth/device/token", null,
                "{\"deviceUuid\":\"" + deviceUuid + "\",\"deviceSecret\":\"wrong-secret\"}");
        expect(401, "POST", "/api/auth/device/token", null,
                "{\"deviceUuid\":\"" + UUID.randomUUID() + "\",\"deviceSecret\":\"" + secret + "\"}");
        expect(400, "POST", "/api/auth/device/pair", null, "{\"code\":\"NOT-A-CODE\"}");
    }

    @Test
    void eachPatientResolvesToTheirOwnLinkedRecord() {
        String caregiverA = caregiverToken("Asha Menon");
        String caregiverB = caregiverToken("Vikram Shah");
        String patientA = createPatient(caregiverA, "Patient A");
        String patientB = createPatient(caregiverB, "Patient B");

        JsonNode pairedA = pair(text(pairingCode(caregiverA, patientA), "pairingCode"));
        JsonNode pairedB = pair(text(pairingCode(caregiverB, patientB), "pairingCode"));

        assertEquals(patientA, text(expect(200, "GET", "/api/me/patient", accessToken(pairedA), null).body(), "uuid"));
        assertEquals(patientB, text(expect(200, "GET", "/api/me/patient", accessToken(pairedB), null).body(), "uuid"));

        // A second device for the same patient signs in as the same patient account
        JsonNode secondA = pair(text(pairingCode(caregiverA, patientA), "pairingCode"));
        assertEquals(text(pairedA.get("token").get("account"), "uuid"), text(secondA.get("token").get("account"), "uuid"));
        assertNotEquals(text(pairedA, "deviceUuid"), text(secondA, "deviceUuid"));
        assertNotEquals(text(pairedA.get("token").get("account"), "uuid"), text(pairedB.get("token").get("account"), "uuid"));
        assertEquals(patientA, text(expect(200, "GET", "/api/me/patient", accessToken(secondA), null).body(), "uuid"));
    }

    @Test
    void aRevokedDeviceCanNeitherSignInNorUseItsToken() {
        String caregiver = caregiverToken("Meera Iyer");
        String patient = createPatient(caregiver, "Gopal Iyer");
        JsonNode kept = pair(text(pairingCode(caregiver, patient), "pairingCode"));
        JsonNode revoked = pair(text(pairingCode(caregiver, patient), "pairingCode"));
        String revokedToken = accessToken(revoked);
        expect(200, "GET", "/api/me/patient", revokedToken, null);

        expect(204, "DELETE", "/api/patients/" + patient + "/devices/" + text(revoked, "deviceUuid"), caregiver, null);

        // Its token stops working at once, not when it expires
        expect(401, "GET", "/api/me/patient", revokedToken, null);
        expect(401, "POST", "/api/auth/device/token", null,
                "{\"deviceUuid\":\"" + text(revoked, "deviceUuid") + "\",\"deviceSecret\":\"" + text(revoked, "deviceSecret") + "\"}");
        // Revoking twice is harmless
        expect(204, "DELETE", "/api/patients/" + patient + "/devices/" + text(revoked, "deviceUuid"), caregiver, null);

        // The patient's other device is unaffected
        expect(200, "GET", "/api/me/patient", accessToken(kept), null);

        JsonNode list = devices(caregiver, patient);
        assertEquals(2, list.size());
        for (JsonNode device : list) {
            String expected = text(device, "uuid").equals(text(revoked, "deviceUuid")) ? "REVOKED" : "ACTIVE";
            assertEquals(expected, text(device, "status"));
        }
    }

    @Test
    void anExpiredOrCancelledPairingCodeIsRejected() {
        String caregiver = caregiverToken("Farah Khan");
        String patient = createPatient(caregiver, "Salma Khan");

        JsonNode expired = pairingCode(caregiver, patient);
        jdbc.update("UPDATE patient_devices SET pairing_expires_at = now() - interval '1 minute' WHERE uuid = ?::uuid",
                text(expired, "deviceUuid"));
        expect(400, "POST", "/api/auth/device/pair", null, "{\"code\":\"" + text(expired, "pairingCode") + "\"}");
        assertEquals("EXPIRED", text(devices(caregiver, patient).get(0), "status"));

        JsonNode cancelled = pairingCode(caregiver, patient);
        expect(204, "DELETE", "/api/patients/" + patient + "/devices/" + text(cancelled, "deviceUuid"), caregiver, null);
        expect(400, "POST", "/api/auth/device/pair", null, "{\"code\":\"" + text(cancelled, "pairingCode") + "\"}");

        // No patient account was created by the failed attempts
        Integer linked = jdbc.queryForObject(
                "SELECT count(*) FROM patients WHERE uuid = ?::uuid AND user_account_id IS NOT NULL", Integer.class, patient);
        assertEquals(0, linked);
    }

    @Test
    void aPatientTokenReachesOnlyPatientEndpoints() {
        String caregiver = caregiverToken("Kiran Rao");
        String patient = createPatient(caregiver, "Sita Rao");
        JsonNode paired = pair(text(pairingCode(caregiver, patient), "pairingCode"));
        String token = accessToken(paired);

        expect(200, "GET", "/api/me/patient", token, null);
        expect(200, "GET", "/api/auth/me", token, null);

        // The caregiver API, even for the patient's own record
        expect(403, "GET", "/api/patients", token, null);
        expect(403, "POST", "/api/patients", token, "{\"fullName\":\"Someone Else\"}");
        expect(403, "GET", "/api/patients/" + patient, token, null);
        expect(403, "GET", "/api/patients/" + patient + "/caregivers", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/people", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/memories", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/reminders", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/daily-care", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/dashboard", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/alerts", token, null);
        expect(403, "GET", "/api/patients/" + patient + "/game-results", token, null);
        expect(403, "GET", "/api/games", token, null);
        expect(403, "POST", "/api/patients/" + patient + "/devices/pairing-code", token, "{}");
        expect(403, "GET", "/api/patients/" + patient + "/devices", token, null);
        expect(403, "DELETE", "/api/patients/" + patient + "/devices/" + text(paired, "deviceUuid"), token, null);

        // And the reverse: a caregiver is not a patient
        expect(403, "GET", "/api/me/patient", caregiver, null);
        expect(401, "GET", "/api/me/patient", null, null);
    }

    @Test
    void aPatientTokenWithoutADeviceIsRejected() {
        String caregiver = caregiverToken("Nisha Pillai");
        String patient = createPatient(caregiver, "Raman Pillai");
        JsonNode paired = pair(text(pairingCode(caregiver, patient), "pairingCode"));
        AppUser account = userService.findByUuid(UUID.fromString(text(paired.get("token").get("account"), "uuid")))
                .orElseThrow();

        // A validly signed token for the patient's account, but not bound to any device
        String unbound = jwtTokenService.issue(account).value();
        expect(401, "GET", "/api/me/patient", unbound, null);

        // Bound to another patient's device
        String otherCaregiver = caregiverToken("Other Carer");
        String otherPatient = createPatient(otherCaregiver, "Other Patient");
        JsonNode otherDevice = pair(text(pairingCode(otherCaregiver, otherPatient), "pairingCode"));
        String crossed = jwtTokenService.issueForDevice(account, UUID.fromString(text(otherDevice, "deviceUuid"))).value();
        expect(401, "GET", "/api/me/patient", crossed, null);
    }

    @Test
    void onlyEditorsOfThePatientCanManageItsDevices() {
        String owner = caregiverToken("Owner Carer");
        String patient = createPatient(owner, "Protected Patient");
        JsonNode paired = pair(text(pairingCode(owner, patient), "pairingCode"));
        String devicePath = "/api/patients/" + patient + "/devices/" + text(paired, "deviceUuid");

        // A caregiver with no access to the patient: as if it did not exist
        String stranger = caregiverToken("Stranger Carer");
        expect(404, "POST", "/api/patients/" + patient + "/devices/pairing-code", stranger, "{}");
        expect(404, "GET", "/api/patients/" + patient + "/devices", stranger, null);
        expect(404, "DELETE", devicePath, stranger, null);

        // A VIEWER may list but not pair or revoke
        String viewerEmail = uniqueEmail("viewer");
        expect(201, "POST", "/api/auth/register", null,
                "{\"fullName\":\"Viewer Carer\",\"email\":\"" + viewerEmail + "\",\"password\":\"Correct-Horse-9\"}");
        JsonNode viewerLogin = expect(200, "POST", "/api/auth/login", null,
                "{\"email\":\"" + viewerEmail + "\",\"password\":\"Correct-Horse-9\"}").body();
        String viewer = text(viewerLogin, "accessToken");
        expect(201, "POST", "/api/patients/" + patient + "/caregivers", owner,
                "{\"caregiverUserUuid\":\"" + text(viewerLogin.get("account"), "uuid")
                        + "\",\"relationship\":\"SON\",\"accessLevel\":\"VIEWER\"}");
        expect(403, "POST", "/api/patients/" + patient + "/devices/pairing-code", viewer, "{}");
        expect(200, "GET", "/api/patients/" + patient + "/devices", viewer, null);
        expect(403, "DELETE", devicePath, viewer, null);

        // A device can only be revoked under its own patient
        String strangersPatient = createPatient(stranger, "Strangers Patient");
        expect(404, "DELETE", "/api/patients/" + strangersPatient + "/devices/" + text(paired, "deviceUuid"), stranger, null);

        // The device is still active after all of that
        expect(200, "GET", "/api/me/patient", accessToken(paired), null);
    }

    @Test
    void caregiverAuthenticationIsUnchanged() {
        String email = uniqueEmail("carer");
        String register = "{\"fullName\":\"Regular Carer\",\"email\":\"" + email + "\",\"password\":\"Correct-Horse-9\"}";
        expect(201, "POST", "/api/auth/register", null, register);
        expect(409, "POST", "/api/auth/register", null, register);

        JsonNode login = expect(200, "POST", "/api/auth/login", null,
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Correct-Horse-9\"}").body();
        assertEquals("CAREGIVER", text(login.get("account"), "role"));
        String token = text(login, "accessToken");
        assertEquals(email, text(expect(200, "GET", "/api/auth/me", token, null).body(), "email"));
        expect(200, "GET", "/api/patients", token, null);
        expect(200, "GET", "/api/games", token, null);

        expect(401, "POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Wrong-Password-1\"}");
        expect(401, "GET", "/api/patients", null, null);
        expect(401, "GET", "/api/patients", "not-a-token", null);
        expect(200, "GET", "/api/health", null, null);
    }

    @Test
    void onlyPatientAccountsMayLackAnEmailOrPassword() {
        // ck_app_users_credentials_by_role (V6)
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO app_users (full_name, role) VALUES ('No Credentials', 'CAREGIVER')"));
        jdbc.update("INSERT INTO app_users (full_name, role) VALUES ('Patient Account', 'PATIENT')");

        // ck_alerts_type now accepts HELP_REQUEST
        String caregiver = caregiverToken("Alert Carer");
        String patient = createPatient(caregiver, "Alert Patient");
        jdbc.update("""
                INSERT INTO alerts (patient_id, alert_type, category, severity, title, message)
                SELECT id, 'HELP_REQUEST', 'GENERAL', 'HIGH', 'Help requested', 'Asked for help.'
                FROM patients WHERE uuid = ?::uuid
                """, patient);
        JsonNode alerts = expect(200, "GET", "/api/patients/" + patient + "/alerts", caregiver, null).body();
        assertEquals("HELP_REQUEST", text(alerts.get(0), "alertType"));
    }
}
