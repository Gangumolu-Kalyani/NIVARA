package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.GameResultAnswerRequest;
import com.sih.nivara.dto.request.GameResultCreateRequest;
import com.sih.nivara.dto.response.GameResultAnswerResponse;
import com.sih.nivara.dto.response.GameResultResponse;
import com.sih.nivara.dto.response.GameResultSummaryResponse;
import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.GameResultAnswer;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.entity.Place;

/**
 * Explicit conversion between the game-result DTOs and {@link GameResult} and
 * {@link GameResultAnswer}.
 *
 * <p>Pure field copying: no repository, no service, no validation. The patient, the game and
 * every answer subject are resolved and checked by the caller and passed in.
 *
 * <p>The response methods read the patient, game and answer-subject associations, which are
 * lazy, so the result must have been loaded with them fetched or be mapped inside the
 * transaction that loaded it.
 */
public final class GameResultMapper {

    private GameResultMapper() {
        // utility class
    }

    /**
     * Builds a new attempt without its answers. The cognitive domain is copied from the game,
     * never taken from the client: V3 stores it as a snapshot so past analytics do not shift if
     * the game is later reclassified. Omitted counters keep their defaults.
     */
    public static GameResult toEntity(GameResultCreateRequest request, Patient patient, Game game) {
        GameResult result = new GameResult(patient, game, game.getCognitiveDomain(),
                request.difficultyLevel(), request.status(), request.startedAt());
        if (request.uuid() != null) {
            result.setUuid(request.uuid());
        }
        result.setCompletedAt(request.completedAt());
        result.setDurationMs(request.durationMs());
        result.setScore(request.score());
        result.setMaxScore(request.maxScore());
        result.setTotalQuestions(request.totalQuestions());
        result.setCorrectAnswers(request.correctAnswers());
        if (request.mistakes() != null) {
            result.setMistakes(request.mistakes());
        }
        if (request.answerAttempts() != null) {
            result.setAnswerAttempts(request.answerAttempts());
        }
        if (request.hintsUsed() != null) {
            result.setHintsUsed(request.hintsUsed());
        }
        result.setAvgReactionTimeMs(request.avgReactionTimeMs());
        if (request.playedOffline() != null) {
            result.setPlayedOffline(request.playedOffline());
        }
        result.setLanguageCode(request.languageCode());
        return result;
    }

    /**
     * Builds one answer. At most one subject is non-null; the caller has already checked that
     * and resolved it within the attempt's patient. Omitted attempts and hintUsed keep their
     * defaults of 1 and false.
     */
    public static GameResultAnswer toAnswerEntity(GameResultAnswerRequest request,
                                                  Memory memory,
                                                  Person person,
                                                  Place place,
                                                  PersonalObject object) {
        GameResultAnswer answer = new GameResultAnswer(request.questionNumber(), request.questionType(),
                request.correct());
        answer.setMemory(memory);
        answer.setPerson(person);
        answer.setPlace(place);
        answer.setObject(object);
        if (request.attempts() != null) {
            answer.setAttempts(request.attempts());
        }
        answer.setReactionTimeMs(request.reactionTimeMs());
        if (request.hintUsed() != null) {
            answer.setHintUsed(request.hintUsed());
        }
        answer.setAnsweredAt(request.answeredAt());
        return answer;
    }

    public static GameResultResponse toResponse(GameResult result) {
        return new GameResultResponse(
                result.getUuid(),
                result.getPatient().getUuid(),
                GameMapper.toSummary(result.getGame()),
                result.getCognitiveDomain(),
                result.getDifficultyLevel(),
                result.getStatus(),
                result.getStartedAt(),
                result.getCompletedAt(),
                result.getDurationMs(),
                result.getScore(),
                result.getMaxScore(),
                result.getTotalQuestions(),
                result.getCorrectAnswers(),
                result.getMistakes(),
                result.getAnswerAttempts(),
                result.getHintsUsed(),
                result.getAvgReactionTimeMs(),
                result.getAccuracy(),
                result.isPlayedOffline(),
                result.getLanguageCode(),
                result.getCreatedAt(),
                result.getAnswers().stream().map(GameResultMapper::toAnswerResponse).toList());
    }

    /** The history-list form: every field except the answers. */
    public static GameResultSummaryResponse toSummaryResponse(GameResult result) {
        return new GameResultSummaryResponse(
                result.getUuid(),
                result.getPatient().getUuid(),
                GameMapper.toSummary(result.getGame()),
                result.getCognitiveDomain(),
                result.getDifficultyLevel(),
                result.getStatus(),
                result.getStartedAt(),
                result.getCompletedAt(),
                result.getDurationMs(),
                result.getScore(),
                result.getMaxScore(),
                result.getTotalQuestions(),
                result.getCorrectAnswers(),
                result.getMistakes(),
                result.getAnswerAttempts(),
                result.getHintsUsed(),
                result.getAvgReactionTimeMs(),
                result.getAccuracy(),
                result.isPlayedOffline(),
                result.getLanguageCode(),
                result.getCreatedAt());
    }

    public static GameResultAnswerResponse toAnswerResponse(GameResultAnswer answer) {
        return new GameResultAnswerResponse(
                answer.getQuestionNumber(),
                answer.getQuestionType(),
                answer.getMemory() == null ? null : answer.getMemory().getUuid(),
                answer.getPerson() == null ? null : answer.getPerson().getUuid(),
                answer.getPlace() == null ? null : answer.getPlace().getUuid(),
                answer.getObject() == null ? null : answer.getObject().getUuid(),
                answer.isCorrect(),
                answer.getAttempts(),
                answer.getReactionTimeMs(),
                answer.isHintUsed(),
                answer.getAnsweredAt());
    }
}
