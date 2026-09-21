package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.request.GameResultAnswerRequest;
import com.sih.nivara.dto.request.GameResultCreateRequest;
import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.GameResultAnswer;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.entity.Place;
import com.sih.nivara.entity.enums.GameResultStatus;
import com.sih.nivara.repository.GameResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service access to {@link GameResult} records.
 *
 * <p>Attempts are append-only: {@link #record} writes one attempt and all its answers in one
 * transaction, and nothing here changes an attempt afterwards. The phase 5B save and deleteById
 * remain for internal use; no endpoint exposes them.
 *
 * <p>{@link #record} enforces the V3 rules that the schema could not express as CHECK
 * constraints: the difficulty must lie inside the game's own range, the cognitive domain is
 * snapshotted from the game rather than accepted from the client, and every answer subject must
 * belong to the attempt's patient. It also checks, before the insert, the cross-field CHECK
 * constraints of game_results and game_result_answers, so a client mistake is a 400 rather than
 * a database error. Every check runs before anything is persisted.
 */
@Service
@Transactional(readOnly = true)
public class GameResultService {

    /** What {@link #record} did: stored a new attempt, or found one already stored under its uuid. */
    public record Recording(GameResult result, boolean created) {
    }

    /**
     * The instants that can be stored in a timestamptz column and read back unchanged. The lower
     * bound is 1 January 4713 BC, the minimum the PostgreSQL JDBC driver supports; the upper bound
     * is the first instant past PostgreSQL's maximum of 294276 AD. Instant accepts far wider
     * values, which the database would refuse only after the attempt row had been inserted.
     */
    private static final Instant EARLIEST_STORABLE = Instant.parse("-4712-01-01T00:00:00Z");
    private static final Instant AFTER_LATEST_STORABLE = Instant.parse("+294277-01-01T00:00:00Z");

    private final GameResultRepository gameResultRepository;
    private final GameService gameService;
    private final MemoryService memoryService;
    private final PersonService personService;
    private final PlaceService placeService;
    private final PersonalObjectService personalObjectService;

    public GameResultService(GameResultRepository gameResultRepository,
                             GameService gameService,
                             MemoryService memoryService,
                             PersonService personService,
                             PlaceService placeService,
                             PersonalObjectService personalObjectService) {
        this.gameResultRepository = gameResultRepository;
        this.gameService = gameService;
        this.memoryService = memoryService;
        this.personService = personService;
        this.placeService = placeService;
        this.personalObjectService = personalObjectService;
    }

    /** All game results, unfiltered. */
    public List<GameResult> findAll() {
        return gameResultRepository.findAll();
    }

    /** The game result with this id, or empty when none exists. */
    public Optional<GameResult> findById(Long id) {
        return gameResultRepository.findById(id);
    }

    /** The attempt with this public uuid, with its answers, or empty when none exists. */
    public Optional<GameResult> findByUuid(UUID uuid) {
        return gameResultRepository.findByUuid(uuid);
    }

    /** One patient's attempts, most recently started first, without answers. */
    public List<GameResult> findByPatient(Patient patient) {
        return gameResultRepository.findByPatientOrderByStartedAtDescIdDesc(patient);
    }

    /**
     * Records one attempt and its answers for this patient, atomically.
     *
     * <p>A client-supplied uuid makes an offline upload safe to replay: when an attempt with
     * that uuid already exists for this patient, it is returned unchanged and nothing is written
     * (the first upload wins). The same uuid under another patient is a conflict.
     */
    @Transactional
    public Recording record(Patient patient, GameResultCreateRequest request) {
        if (request.uuid() != null) {
            Optional<GameResult> stored = gameResultRepository.findByUuid(request.uuid());
            if (stored.isPresent()) {
                if (!stored.get().getPatient().getUuid().equals(patient.getUuid())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Game result " + request.uuid() + " already belongs to another patient");
                }
                return new Recording(stored.get(), false);
            }
        }

        Game game = gameService.findByCode(request.gameCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No game with code " + request.gameCode()));
        checkConsistency(request, game);

        GameResult result = GameResultMapper.toEntity(request, patient, game);
        for (GameResultAnswerRequest answer : answersOf(request)) {
            result.addAnswer(resolveAnswer(patient, answer));
        }
        return new Recording(gameResultRepository.save(result), true);
    }

    /** Rejects, as 400, a request the database would refuse or that breaks a V3 service rule. */
    private static void checkConsistency(GameResultCreateRequest request, Game game) {
        checkStorable("startedAt", request.startedAt());
        checkStorable("completedAt", request.completedAt());
        for (GameResultAnswerRequest answer : answersOf(request)) {
            checkStorable("answeredAt of question " + answer.questionNumber(), answer.answeredAt());
        }

        short difficulty = request.difficultyLevel();
        if (difficulty < game.getMinDifficulty() || difficulty > game.getMaxDifficulty()) {
            throw badRequest("difficultyLevel " + difficulty + " is outside the range "
                    + game.getMinDifficulty() + ".." + game.getMaxDifficulty() + " of game " + game.getCode());
        }
        if (request.correctAnswers() > request.totalQuestions()) {
            throw badRequest("correctAnswers cannot exceed totalQuestions");
        }
        if (request.maxScore() != null && request.score() > request.maxScore()) {
            throw badRequest("score cannot exceed maxScore");
        }
        if (request.completedAt() != null && request.completedAt().isBefore(request.startedAt())) {
            throw badRequest("completedAt cannot be before startedAt");
        }
        if (request.status() == GameResultStatus.COMPLETED && request.completedAt() == null) {
            throw badRequest("a COMPLETED attempt needs completedAt");
        }

        Set<Short> questionNumbers = new HashSet<>();
        for (GameResultAnswerRequest answer : answersOf(request)) {
            long subjects = Stream.of(answer.memoryUuid(), answer.personUuid(), answer.placeUuid(),
                    answer.objectUuid()).filter(Objects::nonNull).count();
            if (subjects > 1) {
                throw badRequest("question " + answer.questionNumber() + " names more than one subject");
            }
            if (!questionNumbers.add(answer.questionNumber())) {
                throw badRequest("question " + answer.questionNumber() + " appears more than once");
            }
        }
    }

    /** Rejects, as 400, a timestamp the database cannot store. A null optional timestamp passes. */
    private static void checkStorable(String field, Instant value) {
        if (value != null && (value.isBefore(EARLIEST_STORABLE) || !value.isBefore(AFTER_LATEST_STORABLE))) {
            throw badRequest(field + " is outside the storable range " + EARLIEST_STORABLE
                    + " (inclusive) to " + AFTER_LATEST_STORABLE + " (exclusive)");
        }
    }

    /** Builds one answer, resolving its subject within the attempt's patient only. */
    private GameResultAnswer resolveAnswer(Patient patient, GameResultAnswerRequest answer) {
        Memory memory = answer.memoryUuid() == null ? null
                : memoryService.findByUuidAndPatient(answer.memoryUuid(), patient)
                        .orElseThrow(() -> notFound("memory", answer.memoryUuid()));
        Person person = answer.personUuid() == null ? null
                : personService.findByUuidAndPatient(answer.personUuid(), patient)
                        .orElseThrow(() -> notFound("person", answer.personUuid()));
        Place place = answer.placeUuid() == null ? null
                : placeService.findByUuidAndPatient(answer.placeUuid(), patient)
                        .orElseThrow(() -> notFound("place", answer.placeUuid()));
        PersonalObject object = answer.objectUuid() == null ? null
                : personalObjectService.findByUuidAndPatient(answer.objectUuid(), patient)
                        .orElseThrow(() -> notFound("object", answer.objectUuid()));
        return GameResultMapper.toAnswerEntity(answer, memory, person, place, object);
    }

    private static List<GameResultAnswerRequest> answersOf(GameResultCreateRequest request) {
        return request.answers() == null ? List.of() : request.answers();
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /**
     * An answer subject that is unknown or belongs to another patient, reported the way the
     * memory API reports an unresolved reference.
     */
    private static ResponseStatusException notFound(String what, UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No " + what + " " + uuid + " belonging to this patient");
    }

    /** Inserts a new game result or updates an existing one. */
    @Transactional
    public GameResult save(GameResult gameResult) {
        return gameResultRepository.save(gameResult);
    }

    /** Removes the game result with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        gameResultRepository.deleteById(id);
    }
}
