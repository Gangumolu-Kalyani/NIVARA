-- =====================================================================
-- NIVARA v1 - Phase 1: accounts and caregiver access
-- Tables: app_users, patients, patient_caregivers
-- =====================================================================


-- ---------------------------------------------------------------------
-- app_users: every login account (caregivers, admins, optional patient logins)
-- ---------------------------------------------------------------------
CREATE TABLE app_users (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                UUID          NOT NULL DEFAULT gen_random_uuid(),
    full_name           VARCHAR(120)  NOT NULL,
    email               VARCHAR(254)  NOT NULL,
    phone               VARCHAR(16),
    password_hash       VARCHAR(255)  NOT NULL,
    role                VARCHAR(20)   NOT NULL,
    preferred_language  VARCHAR(10)   NOT NULL DEFAULT 'en',
    is_enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uq_app_users_uuid UNIQUE (uuid),
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_app_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_app_users_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+$'),
    CONSTRAINT ck_app_users_phone_format CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'CAREGIVER', 'PATIENT')),
    CONSTRAINT ck_app_users_preferred_language CHECK (preferred_language ~ '^[a-z]{2,3}(-[A-Z]{2})?$')
);


-- ---------------------------------------------------------------------
-- patients: the elderly user's profile; root of all patient-owned data
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_account_id     BIGINT,
    full_name           VARCHAR(120)  NOT NULL,
    preferred_name      VARCHAR(60),
    birth_year          SMALLINT,
    cognitive_stage     VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN',
    preferred_language  VARCHAR(10)   NOT NULL DEFAULT 'en',
    timezone            VARCHAR(40)   NOT NULL DEFAULT 'Asia/Kolkata',
    created_by_user_id  BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT uq_patients_uuid UNIQUE (uuid),
    CONSTRAINT uq_patients_user_account UNIQUE (user_account_id),
    CONSTRAINT fk_patients_user_account
        FOREIGN KEY (user_account_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patients_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_patients_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_patients_birth_year CHECK (birth_year BETWEEN 1900 AND 2100),
    CONSTRAINT ck_patients_cognitive_stage
        CHECK (cognitive_stage IN ('UNKNOWN', 'NONE', 'MILD', 'MODERATE', 'SEVERE')),
    CONSTRAINT ck_patients_preferred_language CHECK (preferred_language ~ '^[a-z]{2,3}(-[A-Z]{2})?$')
);

CREATE INDEX ix_patients_created_by_user_id ON patients (created_by_user_id);


-- ---------------------------------------------------------------------
-- patient_caregivers: which caregiver may access which patient
-- ---------------------------------------------------------------------
CREATE TABLE patient_caregivers (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    patient_id          BIGINT        NOT NULL,
    caregiver_user_id   BIGINT        NOT NULL,
    relationship        VARCHAR(30)   NOT NULL,
    access_level        VARCHAR(10)   NOT NULL DEFAULT 'EDITOR',
    is_primary          BOOLEAN       NOT NULL DEFAULT FALSE,
    receives_alerts     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_patient_caregivers PRIMARY KEY (id),
    CONSTRAINT uq_patient_caregivers_patient_caregiver UNIQUE (patient_id, caregiver_user_id),
    CONSTRAINT fk_patient_caregivers_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_caregivers_caregiver_user
        FOREIGN KEY (caregiver_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_patient_caregivers_relationship
        CHECK (relationship IN ('SPOUSE', 'SON', 'DAUGHTER', 'SON_IN_LAW', 'DAUGHTER_IN_LAW', 'GRANDCHILD',
                                'SIBLING', 'RELATIVE', 'FRIEND', 'NEIGHBOUR', 'CAREGIVER', 'DOCTOR', 'OTHER')),
    CONSTRAINT ck_patient_caregivers_access_level CHECK (access_level IN ('OWNER', 'EDITOR', 'VIEWER'))
);

-- At most one primary caregiver per patient.
CREATE UNIQUE INDEX uq_patient_caregivers_one_primary ON patient_caregivers (patient_id) WHERE is_primary;

-- "Which patients can this caregiver access?"
CREATE INDEX ix_patient_caregivers_caregiver_user_id ON patient_caregivers (caregiver_user_id);
