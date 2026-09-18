package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.RelationshipType;
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
 * Someone the patient knows: family, friend, neighbour or doctor.
 * Maps table people (V2).
 *
 * <p>The link to memories is mapped only from {@link Memory}, so loading a person never
 * drags in their memories.
 */
@Entity
@Table(name = "people")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** The name the patient actually uses for this person. */
    @Column(name = "called_as", length = 60)
    private String calledAs;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private RelationshipType relationship;

    /** Free text for a relationship the enum does not cover. */
    @Column(name = "relationship_label", length = 60)
    private String relationshipLabel;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "include_in_games", nullable = false)
    private boolean includeInGames = true;

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

    protected Person() {
        // required by JPA
    }

    public Person(Patient patient, String fullName, RelationshipType relationship, AppUser createdByUser) {
        this.patient = patient;
        this.fullName = fullName;
        this.relationship = relationship;
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

    public Patient getPatient() {
        return patient;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCalledAs() {
        return calledAs;
    }

    public void setCalledAs(String calledAs) {
        this.calledAs = calledAs;
    }

    public RelationshipType getRelationship() {
        return relationship;
    }

    public void setRelationship(RelationshipType relationship) {
        this.relationship = relationship;
    }

    public String getRelationshipLabel() {
        return relationshipLabel;
    }

    public void setRelationshipLabel(String relationshipLabel) {
        this.relationshipLabel = relationshipLabel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isIncludeInGames() {
        return includeInGames;
    }

    public void setIncludeInGames(boolean includeInGames) {
        this.includeInGames = includeInGames;
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
        if (!(other instanceof Person that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
