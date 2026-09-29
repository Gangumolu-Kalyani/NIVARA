-- =====================================================================
-- NIVARA v1 - Phase 15: assistant conversations and messages
-- Tables: assistant_conversations, assistant_messages
-- Depends on V1: app_users, patients
--
-- Every exchange with the NIVARA assistant is kept, so what the assistant
-- was asked and what it answered can be audited later.
--
-- A conversation belongs to exactly one account and is only ever visible to
-- it. A patient's conversation is always about that patient. A caregiver's
-- conversation may be about one patient the caregiver can reach, or none.
--
-- Rules that cannot be CHECK constraints and are enforced in the Spring
-- service layer:
--   * a PATIENT conversation's patient is the one linked to its owner
--     (patients.user_account_id)
--   * a CAREGIVER conversation's patient is one its owner can reach through
--     patient_caregivers; this is re-checked on every request
--   * messages are numbered 1, 2, 3 ... within their conversation
-- =====================================================================


-- ---------------------------------------------------------------------
-- assistant_conversations: one conversation with the assistant
-- ---------------------------------------------------------------------
CREATE TABLE assistant_conversations (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid            UUID          NOT NULL DEFAULT gen_random_uuid(),
    owner_user_id   BIGINT        NOT NULL,
    patient_id      BIGINT,
    -- Whether the owner talks as the patient or as a caregiver; decides the
    -- assistant's tone and, in later phases, which tools it may use.
    mode            VARCHAR(20)   NOT NULL,
    -- The language the assistant answers in, chosen when the conversation starts.
    language_code   VARCHAR(10)   NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Moves with every new message, so conversations list by latest activity.
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_assistant_conversations PRIMARY KEY (id),
    CONSTRAINT uq_assistant_conversations_uuid UNIQUE (uuid),
    CONSTRAINT fk_assistant_conversations_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assistant_conversations_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assistant_conversations_mode CHECK (mode IN ('PATIENT', 'CAREGIVER')),
    -- A patient always talks about themselves.
    CONSTRAINT ck_assistant_conversations_patient_mode_has_patient
        CHECK (mode <> 'PATIENT' OR patient_id IS NOT NULL),
    CONSTRAINT ck_assistant_conversations_language_code
        CHECK (language_code ~ '^[a-z]{2,3}(-[A-Z]{2})?$'),
    CONSTRAINT ck_assistant_conversations_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_assistant_conversations_closed_rule
        CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

-- "My conversations", most recently active first.
CREATE INDEX ix_assistant_conversations_owner_updated
    ON assistant_conversations (owner_user_id, updated_at DESC);

CREATE INDEX ix_assistant_conversations_patient
    ON assistant_conversations (patient_id) WHERE patient_id IS NOT NULL;


-- ---------------------------------------------------------------------
-- assistant_messages: one turn in a conversation; append-only
-- ---------------------------------------------------------------------
CREATE TABLE assistant_messages (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid              UUID          NOT NULL DEFAULT gen_random_uuid(),
    conversation_id   BIGINT        NOT NULL,
    sequence_number   INTEGER       NOT NULL,
    sender            VARCHAR(20)   NOT NULL,
    -- The account that wrote a USER message; NULL for the assistant's replies.
    sender_user_id    BIGINT,
    content           TEXT          NOT NULL,
    -- What produced an ASSISTANT message, for example 'placeholder' or a model id;
    -- NULL for USER messages.
    generated_by      VARCHAR(100),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_assistant_messages PRIMARY KEY (id),
    CONSTRAINT uq_assistant_messages_uuid UNIQUE (uuid),
    CONSTRAINT uq_assistant_messages_sequence UNIQUE (conversation_id, sequence_number),
    CONSTRAINT fk_assistant_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_assistant_messages_sender_user
        FOREIGN KEY (sender_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assistant_messages_sequence_number CHECK (sequence_number >= 1),
    CONSTRAINT ck_assistant_messages_sender CHECK (sender IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_assistant_messages_content_not_blank CHECK (btrim(content) <> ''),
    -- A user message names who wrote it; an assistant message names what generated it.
    CONSTRAINT ck_assistant_messages_attribution
        CHECK ((sender = 'USER' AND sender_user_id IS NOT NULL AND generated_by IS NULL)
            OR (sender = 'ASSISTANT' AND sender_user_id IS NULL AND generated_by IS NOT NULL))
);

CREATE INDEX ix_assistant_messages_sender_user
    ON assistant_messages (sender_user_id) WHERE sender_user_id IS NOT NULL;
