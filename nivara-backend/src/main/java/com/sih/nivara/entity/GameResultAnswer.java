package com.sih.nivara.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One question or round inside a game attempt, and the personalized-memory item it asked about.
 * Maps table game_result_answers (V3).
 *
 * <p>At most one of memory, person, place or object is set; the database enforces that with
 * ck_game_result_answers_single_subject. The subject must belong to the same patient as the
 * result, which is a service-layer rule.
 *
 * <p>questionType is a plain String on purpose: the column has no CHECK constraint, so the games
 * module can add question types without a migration.
 */
@Entity
@Table(name = "game_result_answers")
public class GameResultAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_result_id", nullable = false, updatable = false)
    private GameResult gameResult;

    @Column(name = "question_number", nullable = false)
    private short questionNumber;

    @Column(name = "question_type", nullable = false, length = 40)
    private String questionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memory_id")
    private Memory memory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id")
    private PersonalObject object;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "attempts", nullable = false)
    private short attempts = 1;

    @Column(name = "reaction_time_ms")
    private Integer reactionTimeMs;

    @Column(name = "hint_used", nullable = false)
    private boolean hintUsed = false;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected GameResultAnswer() {
        // required by JPA
    }

    public GameResultAnswer(short questionNumber, String questionType, boolean correct) {
        this.questionNumber = questionNumber;
        this.questionType = questionType;
        this.correct = correct;
    }

    public Long getId() {
        return id;
    }

    public GameResult getGameResult() {
        return gameResult;
    }

    /** Set through GameResult.addAnswer / removeAnswer, which keep both sides consistent. */
    void setGameResult(GameResult gameResult) {
        this.gameResult = gameResult;
    }

    public short getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(short questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place place) {
        this.place = place;
    }

    public PersonalObject getObject() {
        return object;
    }

    public void setObject(PersonalObject object) {
        this.object = object;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public short getAttempts() {
        return attempts;
    }

    public void setAttempts(short attempts) {
        this.attempts = attempts;
    }

    public Integer getReactionTimeMs() {
        return reactionTimeMs;
    }

    public void setReactionTimeMs(Integer reactionTimeMs) {
        this.reactionTimeMs = reactionTimeMs;
    }

    public boolean isHintUsed() {
        return hintUsed;
    }

    public void setHintUsed(boolean hintUsed) {
        this.hintUsed = hintUsed;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameResultAnswer that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
