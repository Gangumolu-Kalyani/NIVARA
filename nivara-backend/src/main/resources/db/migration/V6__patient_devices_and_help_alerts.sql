-- =====================================================================
-- NIVARA v1 - Phase 14: patient accounts, paired patient devices, and
-- the HELP_REQUEST alert type
-- Depends on V1: app_users, patients
-- Depends on V5: alerts
--
-- A patient signs in on a device a caregiver has paired, never with an
-- email and password: a caregiver creates a short one-time pairing code,
-- the patient's device redeems it, and from then on the device holds a
-- long-lived secret it exchanges for ordinary access tokens.
--
-- Rules that cannot be CHECK constraints and are enforced in the Spring
-- service layer:
--   * patients.user_account_id only ever names an account whose role is
--     PATIENT, and that account is linked to no other patient
--   * a device's access tokens are only accepted while the device is
--     paired and not revoked (AccountJwtAuthenticationConverter)
-- =====================================================================


-- ---------------------------------------------------------------------
-- app_users: PATIENT accounts have no email or password
-- ---------------------------------------------------------------------
ALTER TABLE app_users
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL;

-- Every other role still signs in with email and password. uq_app_users_email
-- and ck_app_users_email_format keep holding: NULLs are distinct and pass CHECKs.
ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_credentials_by_role
        CHECK (role = 'PATIENT' OR (email IS NOT NULL AND password_hash IS NOT NULL));


-- ---------------------------------------------------------------------
-- patient_devices: a device a patient uses, paired by a caregiver
-- ---------------------------------------------------------------------
CREATE TABLE patient_devices (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id           BIGINT        NOT NULL,
    -- A caregiver's name for the device, for example 'Kitchen tablet'.
    label                VARCHAR(60),
    created_by_user_id   BIGINT        NOT NULL,
    -- SHA-256 (hex) of the one-time pairing code; cleared once redeemed.
    pairing_code_hash    VARCHAR(64),
    pairing_expires_at   TIMESTAMPTZ,
    -- SHA-256 (hex) of the device secret; set when the device is paired.
    secret_hash          VARCHAR(64),
    paired_at            TIMESTAMPTZ,
    last_used_at         TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_patient_devices PRIMARY KEY (id),
    CONSTRAINT uq_patient_devices_uuid UNIQUE (uuid),
    CONSTRAINT fk_patient_devices_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_devices_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_patient_devices_label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT ck_patient_devices_pairing_code_hash CHECK (pairing_code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_patient_devices_secret_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    -- Before pairing a device has a code and its expiry and no secret; after
    -- pairing it has a secret and no code. It is never both, or neither.
    CONSTRAINT ck_patient_devices_pairing_state
        CHECK ((paired_at IS NULL
                    AND pairing_code_hash IS NOT NULL AND pairing_expires_at IS NOT NULL
                    AND secret_hash IS NULL)
            OR (paired_at IS NOT NULL
                    AND pairing_code_hash IS NULL AND pairing_expires_at IS NULL
                    AND secret_hash IS NOT NULL)),
    CONSTRAINT ck_patient_devices_revoked_after_created CHECK (revoked_at >= created_at)
);

CREATE INDEX ix_patient_devices_patient ON patient_devices (patient_id);

-- Redeeming a code looks it up by hash; a code can match at most one device.
CREATE UNIQUE INDEX uq_patient_devices_pairing_code
    ON patient_devices (pairing_code_hash) WHERE pairing_code_hash IS NOT NULL;

CREATE INDEX ix_patient_devices_created_by ON patient_devices (created_by_user_id);


-- ---------------------------------------------------------------------
-- alerts: a patient asking for help outside any reminder
-- ---------------------------------------------------------------------
ALTER TABLE alerts DROP CONSTRAINT ck_alerts_type;
ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_type CHECK (alert_type IN ('REMINDER_ESCALATION', 'HELP_REQUEST', 'OTHER'));
