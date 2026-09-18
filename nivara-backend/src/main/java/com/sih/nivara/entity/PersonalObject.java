package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.ObjectCategory;
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
 * An everyday object the patient uses or looks for, such as spectacles or a walking stick.
 * Maps table personal_objects (V2). Named PersonalObject to avoid clashing with java.lang.Object.
 */
@Entity
@Table(name = "personal_objects")
public class PersonalObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ObjectCategory category;

    /** Where the object is normally kept, for example "on the bedside table". */
    @Column(name = "usual_location", length = 200)
    private String usualLocation;

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

    protected PersonalObject() {
        // required by JPA
    }

    public PersonalObject(Patient patient, String name, ObjectCategory category, AppUser createdByUser) {
        this.patient = patient;
        this.name = name;
        this.category = category;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObjectCategory getCategory() {
        return category;
    }

    public void setCategory(ObjectCategory category) {
        this.category = category;
    }

    public String getUsualLocation() {
        return usualLocation;
    }

    public void setUsualLocation(String usualLocation) {
        this.usualLocation = usualLocation;
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
        if (!(other instanceof PersonalObject that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
