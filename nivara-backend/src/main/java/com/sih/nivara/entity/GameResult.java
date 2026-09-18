package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameResultStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One attempt at one game by one patient. Maps table game_results (V3).
 *
 * <p>Rows are append-only, so this table has no updated_at, no soft delete and no version column.
 * The uuid may come from the device, which makes an offline upload safe to replay.
 */
@Entity
@Table(name = "game_results")
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    /** Set when the attempt came from an AI suggestion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", updatable = false)
    private GameRecommendation recommendation;

    /**
     * Historical snapshot of the game's cognitive domain at the time of the attempt, so past
     * dashboard and AI analytics do not change if the game is later reclassified. Written by the
     * backend from Game.cognitiveDomain and never updated afterwards.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_domain", nullable = false, length = 30, updatable = false)
    private CognitiveDomain cognitiveDomain;

    @Column(name = "difficulty_level", nullable = false)
    private short difficultyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameResultStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "total_questions", nullable = false)
    private short totalQuestions;

    @Column(name = "correct_answers", nullable = false)
    private short correctAnswers;

    @Column(name = "mistakes", nullable = false)
    private short mistakes;

    @Column(name = "answer_attempts", nullable = false)
    private short answerAttempts;

    @Column(name = "hints_used", nullable = false)
    private short hintsUsed;

    @Column(name = "avg_reaction_time_ms")
    private Integer avgReactionTimeMs;

    /**
     * Database-generated percentage: round((100.0 * correct_answers) / NULLIF(total_questions, 0), 2).
     * Read-only in Java: never inserted or updated, and null when no question was asked.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "accuracy", precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal accuracy;

    @Column(name = "played_offline", nullable = false)
    private boolean playedOffline = false;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The questions of this attempt. A true composition: the database declares it with the only
     * ON DELETE CASCADE in the schema, and answers are uploaded together with their result.
     */
    @OneToMany(mappedBy = "gameResult", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    @OrderBy("questionNumber ASC")
    private List<GameResultAnswer> answers = new ArrayList<>();

    protected GameResult() {
        // required by JPA
    }

    public GameResult(Patient patient, Game game, CognitiveDomain cognitiveDomain, short difficultyLevel,
                      GameResultStatus status, Instant startedAt) {
        this.patient = patient;
        this.game = game;
        this.cognitiveDomain = cognitiveDomain;
        this.difficultyLevel = difficultyLevel;
        this.status = status;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Patient getPatient() {
        return patient;
    }

    public Game getGame() {
        return game;
    }

    public GameRecommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(GameRecommendation recommendation) {
        this.recommendation = recommendation;
    }

    public CognitiveDomain getCognitiveDomain() {
        return cognitiveDomain;
    }

    public short getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(short difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public GameResultStatus getStatus() {
        return status;
    }

    public void setStatus(GameResultStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public short getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(short totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public short getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(short correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public short getMistakes() {
        return mistakes;
    }

    public void setMistakes(short mistakes) {
        this.mistakes = mistakes;
    }

    public short getAnswerAttempts() {
        return answerAttempts;
    }

    public void setAnswerAttempts(short answerAttempts) {
        this.answerAttempts = answerAttempts;
    }

    public short getHintsUsed() {
        return hintsUsed;
    }

    public void setHintsUsed(short hintsUsed) {
        this.hintsUsed = hintsUsed;
    }

    public Integer getAvgReactionTimeMs() {
        return avgReactionTimeMs;
    }

    public void setAvgReactionTimeMs(Integer avgReactionTimeMs) {
        this.avgReactionTimeMs = avgReactionTimeMs;
    }

    /** Calculated by PostgreSQL; there is deliberately no setter. */
    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public boolean isPlayedOffline() {
        return playedOffline;
    }

    public void setPlayedOffline(boolean playedOffline) {
        this.playedOffline = playedOffline;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<GameResultAnswer> getAnswers() {
        return answers;
    }

    /** Keeps both sides of the association consistent. */
    public void addAnswer(GameResultAnswer answer) {
        answers.add(answer);
        answer.setGameResult(this);
    }

    public void removeAnswer(GameResultAnswer answer) {
        answers.remove(answer);
        answer.setGameResult(null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameResult that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
