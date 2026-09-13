-- =====================================================================
-- NIVARA v1 - Phase 2: personalized memory (hybrid model)
-- Tables: people, places, personal_objects, memories, memory_people, memory_objects
-- Depends on V1: patients, app_users
-- =====================================================================


-- ---------------------------------------------------------------------
-- people: people the patient knows (family, friends, doctor)
-- ---------------------------------------------------------------------
CREATE TABLE people (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id          BIGINT        NOT NULL,
    full_name           VARCHAR(120)  NOT NULL,
    called_as           VARCHAR(60),
    relationship        VARCHAR(30)   NOT NULL,
    relationship_label  VARCHAR(60),
    description         TEXT,
    include_in_games    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id  BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_people PRIMARY KEY (id),
    CONSTRAINT uq_people_uuid UNIQUE (uuid),
    CONSTRAINT fk_people_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_people_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_people_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_people_relationship
        CHECK (relationship IN ('SPOUSE', 'SON', 'DAUGHTER', 'SON_IN_LAW', 'DAUGHTER_IN_LAW', 'GRANDCHILD',
                                'SIBLING', 'RELATIVE', 'FRIEND', 'NEIGHBOUR', 'CAREGIVER', 'DOCTOR', 'OTHER'))
);

-- Voice lookup: "Who is Ravi?" / "Who is Chintu?"
CREATE INDEX ix_people_patient_full_name ON people (patient_id, lower(full_name));
CREATE INDEX ix_people_patient_called_as ON people (patient_id, lower(called_as)) WHERE called_as IS NOT NULL;
CREATE INDEX ix_people_created_by_user_id ON people (created_by_user_id);


-- ---------------------------------------------------------------------
-- places: places that matter to the patient
-- ---------------------------------------------------------------------
CREATE TABLE places (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id          BIGINT        NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    place_type          VARCHAR(30)   NOT NULL,
    description         TEXT,
    include_in_games    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id  BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT uq_places_uuid UNIQUE (uuid),
    CONSTRAINT fk_places_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_places_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_places_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_places_place_type
        CHECK (place_type IN ('HOME', 'RELATIVE_HOME', 'MARKET', 'PLACE_OF_WORSHIP', 'HOSPITAL_CLINIC', 'PARK', 'OTHER'))
);

CREATE INDEX ix_places_patient_id ON places (patient_id);
CREATE INDEX ix_places_created_by_user_id ON places (created_by_user_id);


-- ---------------------------------------------------------------------
-- personal_objects: everyday objects the patient uses or looks for
-- ---------------------------------------------------------------------
CREATE TABLE personal_objects (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id          BIGINT        NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    category            VARCHAR(30)   NOT NULL,
    usual_location      VARCHAR(200),
    description         TEXT,
    include_in_games    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id  BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_personal_objects PRIMARY KEY (id),
    CONSTRAINT uq_personal_objects_uuid UNIQUE (uuid),
    CONSTRAINT fk_personal_objects_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personal_objects_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_personal_objects_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_personal_objects_category
        CHECK (category IN ('MOBILITY_AID', 'VISION_HEARING_AID', 'MEDICINE', 'PERSONAL_ITEM', 'HOUSEHOLD', 'OTHER'))
);

-- Voice lookup: "Where are my spectacles?"
CREATE INDEX ix_personal_objects_patient_name ON personal_objects (patient_id, lower(name));
CREATE INDEX ix_personal_objects_created_by_user_id ON personal_objects (created_by_user_id);


-- ---------------------------------------------------------------------
-- memories: things that happened (events and daily activities are memory_type values)
-- ---------------------------------------------------------------------
CREATE TABLE memories (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id           BIGINT        NOT NULL,
    title                VARCHAR(150)  NOT NULL,
    description          TEXT          NOT NULL,
    memory_type          VARCHAR(30)   NOT NULL,
    occurred_on          DATE          NOT NULL,
    time_of_day          VARCHAR(10),
    place_id             BIGINT,
    language_code        VARCHAR(10)   NOT NULL,
    source               VARCHAR(20)   NOT NULL,
    recorded_by_user_id  BIGINT,
    include_in_games     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_memories PRIMARY KEY (id),
    CONSTRAINT uq_memories_uuid UNIQUE (uuid),
    CONSTRAINT fk_memories_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memories_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memories_recorded_by_user
        FOREIGN KEY (recorded_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_memories_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_memories_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT ck_memories_memory_type
        CHECK (memory_type IN ('VISIT', 'OUTING', 'DAILY_ACTIVITY', 'MEAL', 'CELEBRATION', 'HEALTH_EVENT',
                               'CONVERSATION', 'OTHER')),
    CONSTRAINT ck_memories_occurred_on CHECK (occurred_on >= DATE '1900-01-01'),
    CONSTRAINT ck_memories_time_of_day CHECK (time_of_day IN ('MORNING', 'AFTERNOON', 'EVENING', 'NIGHT')),
    CONSTRAINT ck_memories_language_code CHECK (language_code ~ '^[a-z]{2,3}(-[A-Z]{2})?$'),
    CONSTRAINT ck_memories_source CHECK (source IN ('CAREGIVER', 'PATIENT_VOICE', 'SYSTEM')),
    CONSTRAINT ck_memories_caregiver_source_has_recorder
        CHECK (source <> 'CAREGIVER' OR recorded_by_user_id IS NOT NULL)
);

-- "What happened yesterday?" - personalized game generation and voice recall
CREATE INDEX ix_memories_patient_occurred_on ON memories (patient_id, occurred_on DESC);
CREATE INDEX ix_memories_place_id ON memories (place_id) WHERE place_id IS NOT NULL;
CREATE INDEX ix_memories_recorded_by_user_id ON memories (recorded_by_user_id) WHERE recorded_by_user_id IS NOT NULL;


-- ---------------------------------------------------------------------
-- memory_people: who was involved in a memory (memories M:N people)
-- ---------------------------------------------------------------------
CREATE TABLE memory_people (
    memory_id   BIGINT       NOT NULL,
    person_id   BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_memory_people PRIMARY KEY (memory_id, person_id),
    CONSTRAINT fk_memory_people_memory
        FOREIGN KEY (memory_id) REFERENCES memories (id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_people_person
        FOREIGN KEY (person_id) REFERENCES people (id) ON DELETE RESTRICT
);

-- Reverse lookup: "all memories with Ravi" (the primary key already covers memory_id)
CREATE INDEX ix_memory_people_person_id ON memory_people (person_id);


-- ---------------------------------------------------------------------
-- memory_objects: which objects were involved in a memory (memories M:N personal_objects)
-- ---------------------------------------------------------------------
CREATE TABLE memory_objects (
    memory_id   BIGINT       NOT NULL,
    object_id   BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_memory_objects PRIMARY KEY (memory_id, object_id),
    CONSTRAINT fk_memory_objects_memory
        FOREIGN KEY (memory_id) REFERENCES memories (id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_objects_object
        FOREIGN KEY (object_id) REFERENCES personal_objects (id) ON DELETE RESTRICT
);

-- Reverse lookup: "all memories involving the spectacles"
CREATE INDEX ix_memory_objects_object_id ON memory_objects (object_id);
