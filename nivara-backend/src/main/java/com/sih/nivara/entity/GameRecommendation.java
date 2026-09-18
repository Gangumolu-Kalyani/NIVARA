package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.RecommendationStatus;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the AI suggests the patient should play next. Maps table game_recommendations (V3).
 *
 * <p>The status changes after creation, which is why this entity carries optimistic locking.
 * The service layer must check that cognitiveDomain matches the selected game's domain.
 */
@Entity
@Table(name = "game_recommendations")
public class GameRecommendation {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_domain", nullable = false, length = 30)
    private CognitiveDomain cognitiveDomain;

    @Column(name = "recommended_difficulty", nullable = false)
    private short recommendedDifficulty;

    /** Caregiver-readable explanation of the suggestion. */
    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "model_version", nullable = false, length = 50, updatable = false)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecommendationStatus status = RecommendationStatus.PENDING;

    @Column(name = "valid_until")
    private Instant validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected GameRecommendation() {
        // required by JPA
    }

    public GameRecommendation(Patient patient, Game game, CognitiveDomain cognitiveDomain,
                              short recommendedDifficulty, String modelVersion) {
        this.patient = patient;
        this.game = game;
        this.cognitiveDomain = cognitiveDomain;
        this.recommendedDifficulty = recommendedDifficulty;
        this.modelVersion = modelVersion;
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

    public CognitiveDomain getCognitiveDomain() {
        return cognitiveDomain;
    }

    public void setCognitiveDomain(CognitiveDomain cognitiveDomain) {
        this.cognitiveDomain = cognitiveDomain;
    }

    public short getRecommendedDifficulty() {
        return recommendedDifficulty;
    }

    public void setRecommendedDifficulty(short recommendedDifficulty) {
        this.recommendedDifficulty = recommendedDifficulty;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public void setStatus(RecommendationStatus status) {
        this.status = status;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameRecommendation that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
