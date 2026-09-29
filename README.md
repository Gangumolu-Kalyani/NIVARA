# NIVARA

**NIVARA** is a personalized cognitive and memory-assistance platform for elderly users, built for the Smart India Hackathon. Caregivers record the people, places, objects and everyday events in a patient's life, and the patient plays cognitive games built around those memories. The results feed progress tracking and, later, AI-driven recommendations.

This repository contains:

- **`nivara-backend/`**: a Spring Boot REST API on PostgreSQL (this README)
- **`nivara-frontend/`**: the caregiver dashboard, a React + TypeScript app (see [its README](nivara-frontend/README.md))
- **`qwen_chat.py`**: a scratch script that calls a Qwen model on AWS Bedrock, for the planned AI features. It is not used by the application.

---

## Contents

- [Current status](#current-status)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [Authentication](#authentication)
- [Authorization: the care team](#authorization-the-care-team)
- [API reference](#api-reference)
- [API conventions](#api-conventions)
- [Database](#database)
- [Architecture notes](#architecture-notes)
- [Testing](#testing)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Development history](#development-history)

---

## Current status

| Area | State |
|---|---|
| Caregiver accounts, login, JWT authentication | Done |
| Patients, with care-team authorization (OWNER / EDITOR / VIEWER) | Done |
| Personal memory: people, places, personal objects, memories | Done |
| Game catalog (14 games) and game-result recording | Done |
| Personalized game generation (6 games) with progressive hints | Done |
| Daily Assistance reminders, with nudges and escalation | Done |
| Alert center | Done |
| Caregiver dashboard, progress analytics and daily summary | Done |
| Caregiver frontend | Done (see `nivara-frontend/`) |
| Game recommendations (AI) | Schema only, no API yet |
| Voice assistant, patient login, push notifications | Not started |

---

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Data JPA / Hibernate 7, Bean Validation) |
| Security | Spring Security 7 as an OAuth2 resource server, with HS256 JWTs signed by the backend itself; BCrypt password hashing |
| Database | PostgreSQL 13 or newer (developed on 18.6) |
| Migrations | Flyway |
| Build | Maven, through the bundled wrapper (`mvnw` / `mvnw.cmd`) |

---

## Repository layout

```
NIVARA/
├── nivara-frontend/                  # caregiver dashboard (React + Vite); see its README
└── nivara-backend/
    ├── pom.xml
    ├── .env.example                  # copy to .env and fill in (never commit .env)
    └── src/main/
        ├── java/com/sih/nivara/
        │   ├── controller/           # REST endpoints; DTOs in, DTOs out
        │   ├── dto/
        │   │   ├── request/          # request bodies with validation annotations
        │   │   ├── response/         # response bodies (never entities)
        │   │   └── mapper/           # explicit entity <-> DTO conversion
        │   ├── entity/               # JPA entities mapped onto the Flyway schema
        │   │   └── enums/            # one enum per CHECK-constrained column
        │   ├── repository/           # Spring Data JPA repositories
        │   ├── service/              # transactions, business rules, authorization, reminder scheduler
        │   ├── game/                 # game generation, hints and result submission
        │   └── security/             # JWT, CORS, SecurityConfig, CurrentUserProvider
        └── resources/
            ├── application.properties
            └── db/migration/         # V1 to V5 (Flyway)
```

---

## Getting started

### Prerequisites

- **JDK 21**
- **PostgreSQL 13+**. The schema relies on the built-in `gen_random_uuid()`.

You don't need to install Maven; use the wrapper.

### 1. Create the database

```sql
CREATE USER nivara_app WITH PASSWORD 'choose-a-password';
CREATE DATABASE nivara OWNER nivara_app;
```

Flyway creates every table on the first start. Don't create any tables by hand.

### 2. Configure

Copy `nivara-backend/.env.example` to `nivara-backend/.env` and fill it in:

```properties
DB_URL=jdbc:postgresql://localhost:5432/nivara
DB_USERNAME=nivara_app
DB_PASSWORD=choose-a-password
# At least 32 random bytes. For example: openssl rand -base64 48
JWT_SECRET=
# Optional: browser origins allowed to call the API, comma-separated.
# Defaults to the Vite dev server, http://localhost:5173.
CORS_ALLOWED_ORIGINS=
```

- **`JWT_SECRET` is required, and has no default.** The application refuses to start if it is missing or shorter than 32 bytes, and tells you which.
- `.env` is read from the working directory, so run the application from `nivara-backend/`.
- `.env` is git-ignored. Never commit it, and never share the secret.

### 3. Run

```bash
cd nivara-backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

On first start Flyway applies V1–V5 and seeds the game catalog. Check that it's up:

```bash
curl http://localhost:8080/api/health
# NIVARA backend is running
```

---

## Authentication

Every endpoint needs a bearer token **except** these three:

- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`

```bash
# 1. Register a caregiver account
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Ravi Kumar","email":"ravi@example.in","password":"Correct-Horse-9"}'

# 2. Log in. The response holds accessToken, tokenType, expiresAt and account
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ravi@example.in","password":"Correct-Horse-9"}'

# 3. Send the token on every other request
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
```

**Registration**
- Registration is open, and always creates a `CAREGIVER` account. A `role` in the body is ignored.
- Emails are stored in lowercase, so login is case-insensitive.
- Passwords must be 8–72 characters and at most 72 bytes (BCrypt's limit). They are stored as BCrypt hashes and never returned.

**Tokens**
- HS256 JWTs.
- Claims: `sub` is the account uuid, plus `role`, `iss=nivara`, `iat` and `exp`.
- Valid for **60 minutes**. There is no refresh token, so log in again when it expires.

**Login failures**
- A wrong password, an unknown email and a disabled account all return the **same 401**. The response never reveals whether an email is registered.

**Token checks**
- The account behind a token is re-checked on every request. Once an account is disabled or deleted, its existing tokens return 401.

---

## Authorization: the care team

Authentication identifies the caller. **Authorization** decides which patients that caller can reach. It is decided entirely on the server, from the `patient_caregivers` table, on every request.

| Access level | Can do |
|---|---|
| `VIEWER` | read the patient and all of its data (memories, people, places, objects, game results) and see the care team |
| `EDITOR` | everything a VIEWER can, plus create and update data, record game results, and edit the patient profile |
| `OWNER` | everything an EDITOR can, plus grant, change and revoke other caregivers' access |

**Rules**

- Whoever creates a patient becomes its **OWNER** and **primary caregiver**, in the same transaction.
- `GET /api/patients` lists only the patients the caller has access to. Each entry includes the caller's own `accessLevel`.
- **No access at all:** a patient the caller isn't linked to answers **404**, exactly as if it didn't exist. So do its memories, people, places, objects and game results. Patient uuids can't be probed.
- **Too low a level:** a caller who *is* linked but lacks the level (for example a VIEWER trying to write) gets **403**.
- A patient always keeps **at least one OWNER** and **exactly one primary caregiver**:
  - The last OWNER can't be demoted or removed.
  - Setting `primary: true` on another caregiver *moves* the role to them.
  - The current primary can't be cleared or removed until the role has been moved.
- Revoked access takes effect **immediately**, even for tokens issued earlier.
- `ADMIN` accounts get **no bypass**. Only the care-team link counts.
- The `accessLevel` field in responses is only a hint for the UI. The server never trusts it.

---

## API reference

Base URL: `http://localhost:8080`. **Access** is the minimum care-team level the caller needs for that patient.

### Accounts

| Method | Path | Access | Result |
|---|---|---|---|
| POST | `/api/auth/register` | public | 201 + `Location: /api/auth/me` |
| POST | `/api/auth/login` | public | 200: token and account |
| GET | `/api/auth/me` | any token | 200: the caller's account |
| GET | `/api/health` | public | 200 |

### Patients

| Method | Path | Access | Result |
|---|---|---|---|
| POST | `/api/patients` | any token | 201; the caller becomes OWNER. Optional `relationship`, default `CAREGIVER` |
| GET | `/api/patients` | any token | 200: only the caller's patients |
| GET | `/api/patients/{uuid}` | VIEWER | 200 |
| PUT | `/api/patients/{uuid}` | EDITOR | 200 |

### Care team

| Method | Path | Access | Result |
|---|---|---|---|
| GET | `/api/patients/{p}/caregivers` | VIEWER | 200: team members (name, relationship, level, primary; no emails) |
| POST | `/api/patients/{p}/caregivers` | OWNER | 201. Body: `caregiverUserUuid`, `relationship`, optional `accessLevel` (default EDITOR), `primary`, `receivesAlerts` |
| PUT | `/api/patients/{p}/caregivers/{caregiverUuid}` | OWNER | 200. Body: `accessLevel`, `relationship`, `primary`, `receivesAlerts` |
| DELETE | `/api/patients/{p}/caregivers/{caregiverUuid}` | OWNER | 204 |

To grant access, the OWNER needs the other caregiver's account uuid. That caregiver can find it with `GET /api/auth/me`.

### Personal memory

People, places and objects are a patient's reference data. Memories are events that can link to them.

| Method | Path | Access |
|---|---|---|
| POST / GET | `/api/patients/{p}/people` | EDITOR / VIEWER |
| GET / PUT | `/api/people/{uuid}` | VIEWER / EDITOR |
| POST / GET | `/api/patients/{p}/places` | EDITOR / VIEWER |
| GET / PUT | `/api/places/{uuid}` | VIEWER / EDITOR |
| POST / GET | `/api/patients/{p}/objects` | EDITOR / VIEWER |
| GET / PUT | `/api/objects/{uuid}` | VIEWER / EDITOR |
| POST / GET | `/api/patients/{p}/memories` | EDITOR / VIEWER |
| GET / PUT | `/api/memories/{uuid}` | VIEWER / EDITOR |

**Memories**
- A memory can name one `placeUuid` and any number of `peopleUuids` and `objectUuids`.
- Every one of them must belong to the **same patient**. A reference to another patient's data answers 404.
- The memory is recorded as captured by the caller.
- Its `occurredOn` date can't be in the future.

**Lists**
- People are listed by name, places and objects by name, and memories newest first.

### Games and results

| Method | Path | Access | Result |
|---|---|---|---|
| GET | `/api/games` | any token | 200: active games, by name |
| GET | `/api/games/{code}` | any token | 200: any game, active or not |
| POST | `/api/patients/{p}/game-results` | EDITOR | 201 + `Location: /api/game-results/{uuid}` |
| GET | `/api/patients/{p}/game-results` | VIEWER | 200: history, newest `startedAt` first, **without** answers |
| GET | `/api/game-results/{uuid}` | VIEWER | 200: one attempt **with** its answers |

**How a game result is recorded**
- An attempt and all its answers are saved **atomically**: all or nothing.
- The server copies `cognitiveDomain` from the game. `accuracy` is computed by the database.
- `difficultyLevel` must be inside the game's own `minDifficulty..maxDifficulty` range.
- `startedAt` can't be in the future.
- Each answer may name at most one subject (a memory, person, place or object), and that subject must belong to the patient.

**Offline upload**
- A device may send its own `uuid` with an attempt, so an offline upload can be retried safely.
- If the same uuid arrives again for the same patient, the stored attempt is returned with **200** and nothing new is written.
- The same uuid under another patient returns **409**.

The catalog has 14 games. These six can be **generated** from the patient's own data (see [the games module](nivara-backend/GAMES_MODULE_SUMMARY.md)):

- `MEMORY_MATCH`, `MEMORY_TIMELINE`, `REVEAL_REMEMBER`, `FAMILY_RECOGNITION`, `MATCH_IT`, `SPEAK_RECALL`

| Method | Path | Access | Result |
|---|---|---|---|
| GET | `/api/patients/{p}/games/available` | VIEWER | 200: every active game, and whether this patient has enough content to play it |
| POST | `/api/patients/{p}/games/generate` | EDITOR | 201: a game instance built from the patient's data. Body: `gameCode`, `difficulty`, `languageCode` |
| GET | `/api/patients/{p}/games/{instanceId}/hint` | VIEWER | 200: the next hint level |
| POST | `/api/patients/{p}/games/submit-result` | EDITOR | 201: records the attempt, like `POST /game-results` |

These eight are in the catalog for recording results, but have no generator yet:

- `FACE_NAME_MATCH`
- `RELATIONSHIP_RECALL`
- `PLACE_RECOGNITION`
- `OBJECT_RECOGNITION`
- `WHO_VISITED`
- `ROUTINE_RECALL`
- `PATTERN_SEQUENCE`
- `ATTENTION_FOCUS`

### Daily Assistance: reminders

A reminder is a schedule: a category (`MEDICINE`, `HYDRATION`, `APPOINTMENT`, `MOVEMENT`, `COGNITIVE_ACTIVITY`, `MEAL`), a `scheduledTime` in the **patient's timezone**, and a repeat rule: `DAILY`, `WEEKLY` with `repeatDays` (`MON`…`SUN`), or `ONCE` with `oneOffDate`.

| Method | Path | Access | Result |
|---|---|---|---|
| POST | `/api/patients/{p}/reminders` | EDITOR | 201 + `Location`. 400 if a WEEKLY has no days or a ONCE no date |
| GET | `/api/patients/{p}/reminders` | VIEWER | 200: active and paused reminders, by time of day |
| GET / PUT / DELETE | `/api/reminders/{uuid}` | VIEWER / EDITOR / EDITOR | PUT replaces it, including `active`; DELETE answers 204 |
| POST | `/api/reminders/{uuid}/responses` | EDITOR | 200: records the patient's answer. Body: `responseType` (`TAKEN`, `REMIND_LATER`, `NEED_HELP`), optional `scheduledAt` |
| GET | `/api/reminders/{uuid}/responses` | VIEWER | 200: every occurrence of the reminder, newest first |
| GET | `/api/patients/{p}/daily-care?date=` | VIEWER | 200: that day's occurrences in time order; today by default |

**How a reminder plays out**

Each time a reminder falls due, an **occurrence** records what happened:

1. `PENDING` until its time. When it falls due, the patient is nudged and it becomes `SENT`.
2. While no answer comes, it is nudged again every 15 minutes (`nivara.reminders.nudge-interval`).
3. After `escalateAfterMissed` unanswered nudges (1–10, default 2) it becomes `ESCALATED` and an **alert** is raised.
4. The patient's answer: `TAKEN` completes it (`COMPLETED`), even after an escalation. `REMIND_LATER` marks it `SEEN` and restarts the 15 minutes, but not the count. `NEED_HELP` escalates it at once, with a HIGH alert.
5. Still unanswered when the patient's day ends, it becomes `MISSED`.

A background scheduler runs this every minute. Set `nivara.reminders.scheduler.enabled=false` to switch it off.

**Rules**

- Occurrences exist for today and past days only. A reminder created or rescheduled at 10:00 for 09:00 starts tomorrow; it never appears already overdue.
- Changing a reminder's schedule, or switching it off, drops only its future occurrences that nothing has happened to yet. History is always kept.
- DELETE hides the reminder from the API but keeps its occurrence history.

### Alerts

Alerts are raised by the system, never through the API. An unanswered reminder's severity depends on its category: `MEDICINE` and `APPOINTMENT` are HIGH, `MEAL` and `HYDRATION` MEDIUM, `MOVEMENT` and `COGNITIVE_ACTIVITY` LOW.

| Method | Path | Access | Result |
|---|---|---|---|
| GET | `/api/patients/{p}/alerts?status=` | VIEWER | 200: newest first; `status` is optional (`OPEN`, `RESOLVED`, `DISMISSED`) |
| GET | `/api/alerts/{uuid}` | VIEWER | 200 |
| PUT | `/api/alerts/{uuid}/resolve` | EDITOR | 200. 409 if it is already closed |
| PUT | `/api/alerts/{uuid}/dismiss` | EDITOR | 200. 409 if it is already closed |

### Dashboard

All three are read-only views, computed on request, over the patient's calendar days in their timezone.

| Method | Path | Access | Result |
|---|---|---|---|
| GET | `/api/patients/{p}/dashboard` | VIEWER | 200: today's care status, activity cards, reminder counts, upcoming appointments (next 7 days) and open alerts |
| GET | `/api/patients/{p}/progress?range=7` | VIEWER | 200: game performance over the last `range` days (1–90): accuracy, reaction time, per-domain accuracy, difficulty mix, daily trend, recent attempts |
| GET | `/api/patients/{p}/daily-summary?date=` | VIEWER | 200: plain-language summary lines, care rhythm per part of the day, and patterns over the last 7 days; today by default |

- **Care status** is `NEEDS_ATTENTION` when a HIGH alert is open or any of today's reminders was missed or escalated, and `ON_TRACK` otherwise. It describes daily care, never a medical condition.
- **Accuracy** figures average `COMPLETED` attempts only.

### Status codes

| Code | Meaning |
|---|---|
| 200 / 201 / 204 | success; 201 responses carry a `Location` header |
| 400 | invalid body, path or uuid |
| 401 | missing, invalid or expired token, or a disabled account; also a failed login |
| 403 | linked to the patient, but the access level is too low |
| 404 | doesn't exist, **or** the caller has no access to it |
| 409 | duplicate email, duplicate care-team grant, a second primary, removing or demoting the last OWNER, a game-result uuid owned by another patient, answering an already-completed reminder, or closing an already-closed alert |

---

## API conventions

- **UUIDs, never database ids.** Every resource is addressed and returned by its `uuid`. Games are the exception: they are addressed by their immutable `code`.
- **Scoped creation, flat items.** Records are created and listed under their patient (`/api/patients/{p}/…`). After that, each one is addressed on its own (`/api/memories/{uuid}`). A record can never move to another patient.
- **PUT replaces the whole resource.** Leaving out an optional field *clears* it. Required fields must always be sent.
- **Game results are append-only.** There is no PUT or DELETE for them.
- **Enums** are sent and returned as their names, for example `"relationship": "DAUGHTER"`. Accepted values:

| Enum | Values |
|---|---|
| `AccessLevel` | `OWNER`, `EDITOR`, `VIEWER` |
| `RelationshipType` | `SPOUSE`, `SON`, `DAUGHTER`, `SON_IN_LAW`, `DAUGHTER_IN_LAW`, `GRANDCHILD`, `SIBLING`, `RELATIVE`, `FRIEND`, `NEIGHBOUR`, `CAREGIVER`, `DOCTOR`, `OTHER` |
| `CognitiveStage` | `UNKNOWN`, `NONE`, `MILD`, `MODERATE`, `SEVERE` |
| `PlaceType` | `HOME`, `RELATIVE_HOME`, `MARKET`, `PLACE_OF_WORSHIP`, `HOSPITAL_CLINIC`, `PARK`, `OTHER` |
| `ObjectCategory` | `MOBILITY_AID`, `VISION_HEARING_AID`, `MEDICINE`, `PERSONAL_ITEM`, `HOUSEHOLD`, `OTHER` |
| `MemoryType` | `VISIT`, `OUTING`, `DAILY_ACTIVITY`, `MEAL`, `CELEBRATION`, `HEALTH_EVENT`, `CONVERSATION`, `OTHER` |
| `MemorySource` | `CAREGIVER`, `PATIENT_VOICE`, `SYSTEM` |
| `TimeOfDay` | `MORNING`, `AFTERNOON`, `EVENING`, `NIGHT` |
| `GameResultStatus` | `COMPLETED`, `ABANDONED` |
| `ReminderCategory` | `MEDICINE`, `HYDRATION`, `APPOINTMENT`, `MOVEMENT`, `COGNITIVE_ACTIVITY`, `MEAL` |
| `ReminderRepeatType` | `DAILY`, `WEEKLY`, `ONCE` |
| `DayOfWeekCode` | `MON`, `TUE`, `WED`, `THU`, `FRI`, `SAT`, `SUN` |
| `ReminderResponseStatus` | `PENDING`, `SENT`, `SEEN`, `COMPLETED`, `MISSED`, `ESCALATED` |
| `PatientResponseType` | `TAKEN`, `REMIND_LATER`, `NEED_HELP` |
| `AlertSeverity` | `HIGH`, `MEDIUM`, `LOW` |
| `AlertStatus` | `OPEN`, `RESOLVED`, `DISMISSED` |
| `CognitiveDomain` | `MEMORY`, `ATTENTION`, `LANGUAGE`, `EXECUTIVE_FUNCTION`, `ORIENTATION`, `VISUOSPATIAL`, `PROCESSING_SPEED` |

- **Language codes** follow `^[a-z]{2,3}(-[A-Z]{2})?$`, for example `en`, `hi`, `en-IN`.

---

## Database

The schema is owned by **Flyway**. Hibernate runs with `ddl-auto=validate`, so it checks the mapping against the schema and never changes it.

| Migration | Tables |
|---|---|
| `V1__create_accounts_and_caregiver_access.sql` | `app_users`, `patients`, `patient_caregivers` |
| `V2__create_personal_memory_tables.sql` | `people`, `places`, `personal_objects`, `memories`, `memory_people`, `memory_objects` |
| `V3__create_games_and_results.sql` | `games` (seeded with 8 games), `game_recommendations`, `game_results`, `game_result_answers` |
| `V4__add_generated_games_to_catalog.sql` | adds the 6 generated games to `games` |
| `V5__create_reminders_and_alerts.sql` | `reminders`, `reminder_occurrences`, `alerts` |

**Rules for changing the schema**

- **Never edit a migration that has already been applied.** Every schema change is a new `V4__…`, `V5__…` file.
- Most business rules are CHECK constraints in the migrations: allowed values, non-blank names, difficulty ranges, "at most one primary caregiver". Each Java enum mirrors one of those CHECK lists.
- Foreign keys are `ON DELETE RESTRICT`. The two exceptions are compositions: memory links and game-result answers are deleted with their parent.
- Tables that hold patient data have `deleted_at` columns for soft delete. The API doesn't use them yet.

---

## Architecture notes

- **Request flow:** request DTO → controller → service (transactional) → repository → PostgreSQL. On the way back: entity → mapper → response DTO. Entities never leave the service layer.
- **Mappers** are plain static classes that only copy fields. There is no MapStruct.
- **Two classes make every access decision:**
  - `CurrentUserProvider` / `SecurityContextCurrentUserProvider` is the only source of "who is calling". It reads the account from the JWT.
  - `PatientAccessService` is the only place that decides "may they touch this patient".
- **`spring.jpa.open-in-view=false`.** Repositories fetch what each response needs with `@EntityGraph`, so nothing is lazily loaded after the transaction ends.

---

## Testing

- `src/test` currently holds only a Spring context-load test. It needs a running PostgreSQL database and `JWT_SECRET`, just like the application.
- Each phase so far was verified end to end against a running instance, using API test suites for registration, the security edge cases, the full access matrix across every endpoint, cross-patient isolation, validation, transactions, and regression of earlier phases. **Those suites are not in this repository yet.** Moving them into `src/test` as automated integration tests is an open task.

---

## Known limitations

- **No global error handler.** Error responses use Spring's default body without per-field validation messages. A NUL character in any text field returns 500 instead of 400.
- **Concurrent game-result replays.** Several uploads of the *same new* uuid at the same moment can give some of them 500 instead of the 200 replay. Only one attempt is stored, so the data stays correct.
- **No rate limiting** on login.
- **An extra public endpoint.** Spring Security 7 also publishes the standard, non-sensitive `/.well-known/oauth-protected-resource` metadata document without authentication.
- **Lists aren't paged,** and they don't exclude soft-deleted rows.
- **Care-team grant `Location`.** It points to `/api/patients/{p}/caregivers/{uuid}`, which supports PUT and DELETE only.
- **Nudges are recorded, not delivered.** There is no patient app or push channel yet. A nudge records when the patient should have been prompted, and caregivers see escalations in the alert center.
- **Patients can't answer reminders themselves.** Patients have no logins yet, so a caregiver with EDITOR access records the patient's answers.
- **One scheduler instance.** Several backend instances would each run the reminder scheduler. Optimistic locking keeps the data consistent, but they would repeat each other's work.

---

## Roadmap

Likely next steps, not yet scheduled:

- Global exception handling with structured error bodies
- Game recommendations API: the `game_recommendations` table already exists
- Patient logins, via `patients.user_account_id`, so patients can answer their own reminders
- Push notifications for reminder nudges and alerts
- AI recommendations and the voice assistant

---

## Development history

The backend was built in reviewed phases, each verified before the next began.

| Phase | Delivered |
|---|---|
| 1 | PostgreSQL + Flyway; accounts and caregiver-access schema (V1) |
| 2 | Personalized memory schema (V2) |
| 3 | Games, recommendations and results schema, with the seeded game catalog (V3) |
| 4 | JPA entities and enums |
| 5 | Repository and service layers |
| 6 | DTO and validation layer; Patient REST API |
| 7 | Memory REST API |
| 8 | People, places and personal-objects REST APIs |
| 9 | Game catalog and game-result recording |
| 10 | Authentication: accounts, login, JWT |
| 11 | Authorization: caregiver-to-patient access control and the care-team API |
| 12 | Games module: six generated games with hints; catalog entries (V4) |
| 13 | Daily Assistance reminders, alerts and the caregiver dashboard APIs (V5); CORS for the frontend |
