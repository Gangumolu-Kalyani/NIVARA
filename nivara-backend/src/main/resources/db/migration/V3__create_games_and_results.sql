-- =====================================================================
-- NIVARA v1 - Phase 3: cognitive games, results and AI recommendations
-- Tables: games, game_recommendations, game_results, game_result_answers
-- Depends on V1: patients
-- Depends on V2: people, places, personal_objects, memories
--
-- Rules that cannot be CHECK constraints (they need another table or the
-- current date) and are enforced in the Spring service layer:
--   * game_results.difficulty_level must be inside the game's
--     min_difficulty .. max_difficulty range
--   * game_results.cognitive_domain is written by the backend as a snapshot
--     of games.cognitive_domain at the time the attempt is recorded
--   * game_recommendations.cognitive_domain must equal games.cognitive_domain
--     of the recommended game at creation time
--   * every answer subject (memory/person/place/object) must belong to the
--     same patient as the game result
--   * started_at must not be in the future
-- =====================================================================


-- ---------------------------------------------------------------------
-- games: reusable cognitive game definitions (system-level catalog)
-- ---------------------------------------------------------------------
CREATE TABLE games (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY,
    code                  VARCHAR(50)   NOT NULL,
    name                  VARCHAR(100)  NOT NULL,
    description           TEXT,
    cognitive_domain      VARCHAR(30)   NOT NULL,
    content_source        VARCHAR(20)   NOT NULL,
    personalization_type  VARCHAR(20),
    min_content_items     SMALLINT,
    min_difficulty        SMALLINT      NOT NULL DEFAULT 1,
    max_difficulty        SMALLINT      NOT NULL DEFAULT 5,
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_games PRIMARY KEY (id),
    CONSTRAINT uq_games_code UNIQUE (code),
    CONSTRAINT ck_games_code_format CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_games_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_games_cognitive_domain
        CHECK (cognitive_domain IN ('MEMORY', 'ATTENTION', 'LANGUAGE', 'EXECUTIVE_FUNCTION',
                                    'ORIENTATION', 'VISUOSPATIAL', 'PROCESSING_SPEED')),
    CONSTRAINT ck_games_content_source CHECK (content_source IN ('STANDARD', 'PERSONALIZED')),
    CONSTRAINT ck_games_personalization_type
        CHECK (personalization_type IN ('PERSON', 'PLACE', 'OBJECT', 'MEMORY', 'ROUTINE')),
    -- A personalized game says which memory data it draws on; a standard game must not.
    CONSTRAINT ck_games_personalization_rule
        CHECK ((content_source = 'PERSONALIZED') = (personalization_type IS NOT NULL)),
    CONSTRAINT ck_games_min_difficulty CHECK (min_difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_games_max_difficulty CHECK (max_difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_games_difficulty_range CHECK (min_difficulty <= max_difficulty),
    CONSTRAINT ck_games_min_content_items CHECK (min_content_items > 0)
);


-- ---------------------------------------------------------------------
-- game_recommendations: the AI's suggested next game, domain and difficulty
-- ---------------------------------------------------------------------
CREATE TABLE game_recommendations (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id              BIGINT        NOT NULL,
    game_id                 BIGINT        NOT NULL,
    cognitive_domain        VARCHAR(30)   NOT NULL,
    recommended_difficulty  SMALLINT      NOT NULL,
    reason                  TEXT,
    confidence              NUMERIC(4,3),
    model_version           VARCHAR(50)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    valid_until             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_game_recommendations PRIMARY KEY (id),
    CONSTRAINT uq_game_recommendations_uuid UNIQUE (uuid),
    CONSTRAINT fk_game_recommendations_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_recommendations_game
        FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE RESTRICT,
    CONSTRAINT ck_game_recommendations_cognitive_domain
        CHECK (cognitive_domain IN ('MEMORY', 'ATTENTION', 'LANGUAGE', 'EXECUTIVE_FUNCTION',
                                    'ORIENTATION', 'VISUOSPATIAL', 'PROCESSING_SPEED')),
    CONSTRAINT ck_game_recommendations_difficulty CHECK (recommended_difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_game_recommendations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'COMPLETED', 'DISMISSED', 'EXPIRED')),
    CONSTRAINT ck_game_recommendations_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_game_recommendations_model_version CHECK (btrim(model_version) <> '')
);

-- Recommendation history for the caregiver dashboard.
CREATE INDEX ix_game_recommendations_patient_created ON game_recommendations (patient_id, created_at DESC);

-- The current recommendation for a patient (adaptive difficulty).
CREATE INDEX ix_game_recommendations_pending ON game_recommendations (patient_id) WHERE status = 'PENDING';

CREATE INDEX ix_game_recommendations_game_id ON game_recommendations (game_id);


-- ---------------------------------------------------------------------
-- game_results: one attempt (one play session) by one patient; append-only
-- ---------------------------------------------------------------------
CREATE TABLE game_results (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY,
    uuid                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_id            BIGINT        NOT NULL,
    game_id               BIGINT        NOT NULL,
    recommendation_id     BIGINT,
    -- Historical snapshot of games.cognitive_domain at the time of the attempt,
    -- so past dashboard and AI analytics do not change if the game is reclassified.
    cognitive_domain      VARCHAR(30)   NOT NULL,
    difficulty_level      SMALLINT      NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    started_at            TIMESTAMPTZ   NOT NULL,
    completed_at          TIMESTAMPTZ,
    duration_ms           INTEGER       NOT NULL,
    score                 INTEGER       NOT NULL,
    max_score             INTEGER,
    total_questions       SMALLINT      NOT NULL,
    correct_answers       SMALLINT      NOT NULL,
    mistakes              SMALLINT      NOT NULL DEFAULT 0,
    answer_attempts       SMALLINT      NOT NULL DEFAULT 0,
    hints_used            SMALLINT      NOT NULL DEFAULT 0,
    avg_reaction_time_ms  INTEGER,
    -- NULLIF keeps an attempt with no questions (an abandoned session) from
    -- dividing by zero: accuracy is then NULL instead of raising an error.
    accuracy              NUMERIC(5,2)  GENERATED ALWAYS AS
                              (round((100.0 * correct_answers) / NULLIF(total_questions, 0), 2)) STORED,
    played_offline        BOOLEAN       NOT NULL DEFAULT FALSE,
    language_code         VARCHAR(10),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_game_results PRIMARY KEY (id),
    CONSTRAINT uq_game_results_uuid UNIQUE (uuid),
    CONSTRAINT fk_game_results_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_results_game
        FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_results_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES game_recommendations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_game_results_cognitive_domain
        CHECK (cognitive_domain IN ('MEMORY', 'ATTENTION', 'LANGUAGE', 'EXECUTIVE_FUNCTION',
                                    'ORIENTATION', 'VISUOSPATIAL', 'PROCESSING_SPEED')),
    CONSTRAINT ck_game_results_difficulty_level CHECK (difficulty_level BETWEEN 1 AND 5),
    CONSTRAINT ck_game_results_status CHECK (status IN ('COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_game_results_duration_ms CHECK (duration_ms >= 0),
    CONSTRAINT ck_game_results_score CHECK (score >= 0),
    CONSTRAINT ck_game_results_max_score CHECK (max_score >= 0),
    CONSTRAINT ck_game_results_score_within_max CHECK (max_score IS NULL OR score <= max_score),
    CONSTRAINT ck_game_results_total_questions CHECK (total_questions >= 0),
    CONSTRAINT ck_game_results_correct_answers CHECK (correct_answers BETWEEN 0 AND total_questions),
    CONSTRAINT ck_game_results_mistakes CHECK (mistakes >= 0),
    CONSTRAINT ck_game_results_answer_attempts CHECK (answer_attempts >= 0),
    CONSTRAINT ck_game_results_hints_used CHECK (hints_used >= 0),
    CONSTRAINT ck_game_results_avg_reaction_time CHECK (avg_reaction_time_ms >= 0),
    CONSTRAINT ck_game_results_completed_after_start
        CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT ck_game_results_completed_has_timestamp
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT ck_game_results_language_code CHECK (language_code ~ '^[a-z]{2,3}(-[A-Z]{2})?$')
);

-- Dashboard history and trends; the AI's "results since ..." queries.
CREATE INDEX ix_game_results_patient_started ON game_results (patient_id, started_at DESC);

-- Latest difficulty per game, for adaptive difficulty.
CREATE INDEX ix_game_results_patient_game_started ON game_results (patient_id, game_id, started_at DESC);

CREATE INDEX ix_game_results_game_id ON game_results (game_id);

-- "Was the AI recommendation actually played?"
CREATE INDEX ix_game_results_recommendation_id ON game_results (recommendation_id)
    WHERE recommendation_id IS NOT NULL;


-- ---------------------------------------------------------------------
-- game_result_answers: one question/round inside an attempt, and the
-- personalized-memory item it asked about
-- ---------------------------------------------------------------------
CREATE TABLE game_result_answers (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    game_result_id    BIGINT       NOT NULL,
    question_number   SMALLINT     NOT NULL,
    question_type     VARCHAR(40)  NOT NULL,
    memory_id         BIGINT,
    person_id         BIGINT,
    place_id          BIGINT,
    object_id         BIGINT,
    is_correct        BOOLEAN      NOT NULL,
    attempts          SMALLINT     NOT NULL DEFAULT 1,
    reaction_time_ms  INTEGER,
    hint_used         BOOLEAN      NOT NULL DEFAULT FALSE,
    answered_at       TIMESTAMPTZ,

    CONSTRAINT pk_game_result_answers PRIMARY KEY (id),
    CONSTRAINT uq_game_result_answers_question UNIQUE (game_result_id, question_number),
    CONSTRAINT fk_game_result_answers_result
        FOREIGN KEY (game_result_id) REFERENCES game_results (id) ON DELETE CASCADE,
    CONSTRAINT fk_game_result_answers_memory
        FOREIGN KEY (memory_id) REFERENCES memories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_result_answers_person
        FOREIGN KEY (person_id) REFERENCES people (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_result_answers_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_result_answers_object
        FOREIGN KEY (object_id) REFERENCES personal_objects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_game_result_answers_question_number CHECK (question_number >= 1),
    CONSTRAINT ck_game_result_answers_attempts CHECK (attempts >= 1),
    CONSTRAINT ck_game_result_answers_reaction_time CHECK (reaction_time_ms >= 0),
    -- A question asks about at most one item: a memory, a person, a place or an object.
    CONSTRAINT ck_game_result_answers_single_subject
        CHECK (num_nonnulls(memory_id, person_id, place_id, object_id) <= 1)
);

-- "Which people / places / objects / memories is the patient forgetting?"
CREATE INDEX ix_game_result_answers_person_id ON game_result_answers (person_id) WHERE person_id IS NOT NULL;
CREATE INDEX ix_game_result_answers_place_id ON game_result_answers (place_id) WHERE place_id IS NOT NULL;
CREATE INDEX ix_game_result_answers_object_id ON game_result_answers (object_id) WHERE object_id IS NOT NULL;
CREATE INDEX ix_game_result_answers_memory_id ON game_result_answers (memory_id) WHERE memory_id IS NOT NULL;


-- ---------------------------------------------------------------------
-- Reference data: the initial NIVARA game catalog.
--
-- These are system-level game definitions only. No patient data is stored
-- here: personalized content is read from the Phase 2 tables (people,
-- places, personal_objects, memories) at play time, chosen by
-- personalization_type.
--
-- min_content_items = how many rows of that personalization_type a patient
-- must have before the game can be generated for them (NULL for standard
-- games, which need no patient content).
--
-- Difficulty is 1..5 for every game; the games team can narrow a range
-- later with its own migration once real difficulty is measured.
--
-- ON CONFLICT keeps the seed safe to apply more than once.
-- ---------------------------------------------------------------------
INSERT INTO games (code, name, description, cognitive_domain, content_source,
                   personalization_type, min_content_items, min_difficulty, max_difficulty)
VALUES
    ('FACE_NAME_MATCH', 'Face and Name Match',
     'Shows a person the patient knows and asks them to choose the correct name. Other familiar people are used as the wrong options.',
     'MEMORY', 'PERSONALIZED', 'PERSON', 4, 1, 5),

    ('RELATIONSHIP_RECALL', 'Who Is This To You?',
     'Asks how a familiar person is related to the patient, for example son, daughter, sibling or neighbour.',
     'MEMORY', 'PERSONALIZED', 'PERSON', 3, 1, 5),

    ('PLACE_RECOGNITION', 'Familiar Places',
     'Asks the patient to recognise a place they know, such as their home, the local market or a place of worship.',
     'VISUOSPATIAL', 'PERSONALIZED', 'PLACE', 4, 1, 5),

    ('OBJECT_RECOGNITION', 'Everyday Objects',
     'Asks the patient to recognise a personal object and recall where it is usually kept.',
     'MEMORY', 'PERSONALIZED', 'OBJECT', 4, 1, 5),

    ('WHO_VISITED', 'Who Visited You?',
     'Asks who took part in a recent event, for example who visited yesterday morning.',
     'MEMORY', 'PERSONALIZED', 'MEMORY', 3, 1, 5),

    ('ROUTINE_RECALL', 'Daily Routine',
     'Asks the patient to recall recent daily activities, such as what they did this morning or in what order.',
     'ORIENTATION', 'PERSONALIZED', 'ROUTINE', 3, 1, 5),

    ('PATTERN_SEQUENCE', 'Pattern Sequence',
     'Shows a sequence of shapes or colours and asks the patient to choose what comes next.',
     'EXECUTIVE_FUNCTION', 'STANDARD', NULL, NULL, 1, 5),

    ('ATTENTION_FOCUS', 'Find the Target',
     'Asks the patient to find a target item among similar items, training attention and concentration.',
     'ATTENTION', 'STANDARD', NULL, NULL, 1, 5)
ON CONFLICT (code) DO NOTHING;
