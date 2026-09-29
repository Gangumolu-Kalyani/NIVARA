-- =====================================================================
-- NIVARA v1 - Phase 13: Daily Assistance reminders and caregiver alerts
-- Tables: reminders, reminder_occurrences, alerts
-- Depends on V1: app_users, patients
--
-- A reminder is a schedule ("Evening medicine, 20:00, every day"). Each
-- time it falls due, one reminder_occurrences row records what happened:
-- whether the patient was nudged, how they answered, and whether it was
-- escalated to the care team. An unanswered or "need help" occurrence
-- raises an alert.
--
-- Rules that cannot be CHECK constraints and are enforced in the Spring
-- service layer:
--   * reminder_occurrences.patient_id equals the reminder's patient_id
--   * alerts.reminder_occurrence_id, when set, belongs to the same patient
--   * reminder_occurrences.local_date is scheduled_at's date in the
--     patient's timezone at the time the occurrence was created
-- =====================================================================


-- ---------------------------------------------------------------------
-- reminders: what the patient should be reminded of, and when
-- ---------------------------------------------------------------------
CREATE TABLE reminders (
    id                     BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id             BIGINT        NOT NULL,
    category               VARCHAR(30)   NOT NULL,
    title                  VARCHAR(150)  NOT NULL,
    instructions           TEXT,
    -- Wall-clock time in the patient's timezone (patients.timezone).
    scheduled_time         TIME          NOT NULL,
    repeat_type            VARCHAR(10)   NOT NULL DEFAULT 'DAILY',
    -- Comma-separated days for WEEKLY reminders, for example 'MON,WED,FRI'.
    repeat_days            VARCHAR(27),
    one_off_date           DATE,
    -- How many nudges may go unanswered before the care team is alerted.
    escalate_after_missed  SMALLINT      NOT NULL DEFAULT 2,
    is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
    -- When the current schedule took effect. Occurrences are only created
    -- from this instant on, so a reminder created or rescheduled at 10:00
    -- for 09:00 does not immediately produce an overdue occurrence.
    effective_from         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by_user_id     BIGINT        NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_reminders PRIMARY KEY (id),
    CONSTRAINT uq_reminders_uuid UNIQUE (uuid),
    CONSTRAINT fk_reminders_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reminders_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reminders_category
        CHECK (category IN ('MEDICINE', 'HYDRATION', 'APPOINTMENT', 'MOVEMENT',
                            'COGNITIVE_ACTIVITY', 'MEAL')),
    CONSTRAINT ck_reminders_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_reminders_repeat_type CHECK (repeat_type IN ('DAILY', 'WEEKLY', 'ONCE')),
    CONSTRAINT ck_reminders_repeat_days_format
        CHECK (repeat_days ~ '^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$'),
    -- Weekly reminders name their days; the others must not.
    CONSTRAINT ck_reminders_weekly_rule
        CHECK ((repeat_type = 'WEEKLY') = (repeat_days IS NOT NULL)),
    -- One-time reminders name their date; the others must not.
    CONSTRAINT ck_reminders_once_rule
        CHECK ((repeat_type = 'ONCE') = (one_off_date IS NOT NULL)),
    CONSTRAINT ck_reminders_escalate_after_missed CHECK (escalate_after_missed BETWEEN 1 AND 10)
);

CREATE INDEX ix_reminders_patient ON reminders (patient_id);

-- The scheduler's "which reminders are live" scan.
CREATE INDEX ix_reminders_active ON reminders (patient_id) WHERE is_active AND deleted_at IS NULL;

CREATE INDEX ix_reminders_created_by ON reminders (created_by_user_id);


-- ---------------------------------------------------------------------
-- reminder_occurrences: one scheduled instance of a reminder
-- ---------------------------------------------------------------------
CREATE TABLE reminder_occurrences (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid             UUID          NOT NULL DEFAULT gen_random_uuid(),
    reminder_id      BIGINT        NOT NULL,
    -- Copied from the reminder so a patient's day can be read without a join.
    patient_id       BIGINT        NOT NULL,
    scheduled_at     TIMESTAMPTZ   NOT NULL,
    -- The patient's calendar day this occurrence belongs to.
    local_date       DATE          NOT NULL,
    -- When the patient was last nudged; NULL until the occurrence falls due.
    notified_at      TIMESTAMPTZ,
    response_status  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    response_type    VARCHAR(20),
    responded_at     TIMESTAMPTZ,
    nudge_count      SMALLINT      NOT NULL DEFAULT 0,
    escalated_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_reminder_occurrences PRIMARY KEY (id),
    CONSTRAINT uq_reminder_occurrences_uuid UNIQUE (uuid),
    -- One occurrence per reminder per instant; lets creation be idempotent.
    CONSTRAINT uq_reminder_occurrences_slot UNIQUE (reminder_id, scheduled_at),
    CONSTRAINT fk_reminder_occurrences_reminder
        FOREIGN KEY (reminder_id) REFERENCES reminders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reminder_occurrences_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reminder_occurrences_status
        CHECK (response_status IN ('PENDING', 'SENT', 'SEEN', 'COMPLETED', 'MISSED', 'ESCALATED')),
    CONSTRAINT ck_reminder_occurrences_response_type
        CHECK (response_type IN ('TAKEN', 'REMIND_LATER', 'NEED_HELP')),
    CONSTRAINT ck_reminder_occurrences_response_pair
        CHECK ((response_type IS NULL) = (responded_at IS NULL)),
    CONSTRAINT ck_reminder_occurrences_completed_is_taken
        CHECK (response_status <> 'COMPLETED' OR response_type = 'TAKEN'),
    CONSTRAINT ck_reminder_occurrences_escalated_has_timestamp
        CHECK (response_status <> 'ESCALATED' OR escalated_at IS NOT NULL),
    CONSTRAINT ck_reminder_occurrences_nudge_count CHECK (nudge_count >= 0)
);

-- A patient's day (daily care timeline, dashboard, daily summary).
CREATE INDEX ix_reminder_occurrences_patient_date
    ON reminder_occurrences (patient_id, local_date, scheduled_at);

-- A reminder's response history.
CREATE INDEX ix_reminder_occurrences_reminder_scheduled
    ON reminder_occurrences (reminder_id, scheduled_at DESC);

-- The scheduler's "still waiting for an answer" scan.
CREATE INDEX ix_reminder_occurrences_open
    ON reminder_occurrences (scheduled_at)
    WHERE response_status IN ('PENDING', 'SENT', 'SEEN');


-- ---------------------------------------------------------------------
-- alerts: something the care team should look at
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id              BIGINT        NOT NULL,
    reminder_occurrence_id  BIGINT,
    alert_type              VARCHAR(30)   NOT NULL,
    category                VARCHAR(30)   NOT NULL,
    severity                VARCHAR(10)   NOT NULL,
    title                   VARCHAR(200)  NOT NULL,
    message                 TEXT          NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    resolved_at             TIMESTAMPTZ,
    resolved_by_user_id     BIGINT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_alerts PRIMARY KEY (id),
    CONSTRAINT uq_alerts_uuid UNIQUE (uuid),
    CONSTRAINT fk_alerts_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_alerts_reminder_occurrence
        FOREIGN KEY (reminder_occurrence_id) REFERENCES reminder_occurrences (id) ON DELETE RESTRICT,
    CONSTRAINT fk_alerts_resolved_by
        FOREIGN KEY (resolved_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_alerts_type CHECK (alert_type IN ('REMINDER_ESCALATION', 'OTHER')),
    CONSTRAINT ck_alerts_category
        CHECK (category IN ('MEDICINE', 'HYDRATION', 'APPOINTMENT', 'MOVEMENT',
                            'COGNITIVE_ACTIVITY', 'MEAL', 'GENERAL')),
    CONSTRAINT ck_alerts_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_alerts_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_alerts_title_not_blank CHECK (btrim(title) <> ''),
    -- An open alert has no closing details; a closed one always has its time.
    CONSTRAINT ck_alerts_closed_rule
        CHECK ((status = 'OPEN') = (resolved_at IS NULL)),
    CONSTRAINT ck_alerts_open_has_no_resolver
        CHECK (status <> 'OPEN' OR resolved_by_user_id IS NULL)
);

-- Alert center and dashboard, newest first.
CREATE INDEX ix_alerts_patient_created ON alerts (patient_id, created_at DESC);

CREATE INDEX ix_alerts_open ON alerts (patient_id) WHERE status = 'OPEN';

CREATE INDEX ix_alerts_reminder_occurrence ON alerts (reminder_occurrence_id)
    WHERE reminder_occurrence_id IS NOT NULL;

CREATE INDEX ix_alerts_resolved_by ON alerts (resolved_by_user_id) WHERE resolved_by_user_id IS NOT NULL;
