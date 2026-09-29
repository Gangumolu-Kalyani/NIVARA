-- =====================================================================
-- NIVARA v1 - Phase 12: catalog entries for the generated games
-- Depends on V3: games
--
-- The games module (com.sih.nivara.game) generates six games from a
-- patient's own data. Generation and result recording both look a game up
-- in this catalog by code, so each generator's getGameCode() needs a row
-- here, or /games/generate answers 404 and /games/submit-result cannot
-- record the attempt.
--
-- personalization_type holds one value, but some of these games draw on
-- several kinds of data. It names the main source; the description lists
-- the rest. min_content_items is the smallest amount of content the
-- generator accepts at difficulty 1 (its canGenerate()).
--
-- The eight V3 games stay as they are: results already recorded against
-- them must keep resolving.
-- ---------------------------------------------------------------------
INSERT INTO games (code, name, description, cognitive_domain, content_source,
                   personalization_type, min_content_items, min_difficulty, max_difficulty)
VALUES
    ('MEMORY_MATCH', 'Memory Match',
     'Matches the faces of people the patient knows with their names. Difficulty sets how many pairs are shown.',
     'MEMORY', 'PERSONALIZED', 'PERSON', 2, 1, 5),

    ('MEMORY_TIMELINE', 'Memory Timeline',
     'Asks the patient to put their own recent memories and events in the order they happened.',
     'ORIENTATION', 'PERSONALIZED', 'MEMORY', 2, 1, 5),

    ('REVEAL_REMEMBER', 'Reveal and Remember',
     'Uncovers a picture of a familiar person, place or object a piece at a time and asks the patient to name it.',
     'VISUOSPATIAL', 'PERSONALIZED', 'PERSON', 1, 1, 5),

    ('FAMILY_RECOGNITION', 'Family Recognition',
     'Asks the patient to recognise family members and familiar people, and at higher levels how they are related.',
     'MEMORY', 'PERSONALIZED', 'PERSON', 2, 1, 5),

    ('MATCH_IT', 'Match It',
     'Asks the patient to match personal objects and familiar places with related pictures and words.',
     'VISUOSPATIAL', 'PERSONALIZED', 'OBJECT', 2, 1, 5),

    ('SPEAK_RECALL', 'Speak and Recall',
     'A spoken game: the patient names familiar objects, places and people, completes sentences and answers recall questions aloud.',
     'LANGUAGE', 'PERSONALIZED', 'OBJECT', 1, 1, 5)
ON CONFLICT (code) DO NOTHING;
