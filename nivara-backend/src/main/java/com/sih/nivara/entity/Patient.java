package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.CognitiveStage;
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

import java.time.Instant;
import java.util.UUID;

/**
 * The elderly user's profile and the root of all patient-owned data.
 * Maps table patients (V1).
 *
 * <p>No collections of memories, results or caregivers are mapped here: those are
 * read through patient-scoped repository queries so they can be filtered and paged.
 */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    /**
     * Optional login account for the patient. Modelled as many-to-one so the association
     * is genuinely lazy; the one-to-one cardinality is enforced by the database constraint
     * uq_patients_user_account.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", unique = true)
    private AppUser userAccount;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** What the voice assistant calls the patient. */
    @Column(name = "preferred_name", length = 60)
    private String preferredName;

    @Column(name = "birth_year")
    private Short birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_stage", nullable = false, length = 20)
    private CognitiveStage cognitiveStage = CognitiveStage.UNKNOWN;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "en";

    /** IANA time zone name; validated with ZoneId in the service layer. */
    @Column(name = "timezone", nullable = false, length = 40)
    private String timezone = "Asia/Kolkata";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AppUser createdByUser;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Patient() {
        // required by JPA
    }

    public Patient(String fullName, AppUser createdByUser) {
        this.fullName = fullName;
        this.createdByUser = createdByUser;
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

    public AppUser getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(AppUser userAccount) {
        this.userAccount = userAccount;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public Short getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Short birthYear) {
        this.birthYear = birthYear;
    }

    public CognitiveStage getCognitiveStage() {
        return cognitiveStage;
    }

    public void setCognitiveStage(CognitiveStage cognitiveStage) {
        this.cognitiveStage = cognitiveStage;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public AppUser getCreatedByUser() {
        return createdByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Patient that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
