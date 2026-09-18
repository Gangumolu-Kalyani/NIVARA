package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameContentSource;
import com.sih.nivara.entity.enums.PersonalizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A reusable cognitive game definition. System-level catalog shared by all patients,
 * so it holds no patient data. Maps table games (V3), seeded with 8 rows by that migration.
 *
 * <p>The public identifier is {@code code}; this table has no uuid, version or soft-delete column.
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Stable public identifier, for example FACE_NAME_MATCH. */
    @Column(name = "code", nullable = false, length = 50, unique = true, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** The canonical, current domain. GameResult keeps its own historical snapshot. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_domain", nullable = false, length = 30)
    private CognitiveDomain cognitiveDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", nullable = false, length = 20)
    private GameContentSource contentSource;

    /** Which Phase 2 data a personalized game draws on; null for standard games. */
    @Enumerated(EnumType.STRING)
    @Column(name = "personalization_type", length = 20)
    private PersonalizationType personalizationType;

    /** How many rows of that type a patient needs before the game can be built. */
    @Column(name = "min_content_items")
    private Short minContentItems;

    @Column(name = "min_difficulty", nullable = false)
    private short minDifficulty = 1;

    @Column(name = "max_difficulty", nullable = false)
    private short maxDifficulty = 5;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Game() {
        // required by JPA
    }

    public Game(String code, String name, CognitiveDomain cognitiveDomain, GameContentSource contentSource) {
        this.code = code;
        this.name = name;
        this.cognitiveDomain = cognitiveDomain;
        this.contentSource = contentSource;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CognitiveDomain getCognitiveDomain() {
        return cognitiveDomain;
    }

    public void setCognitiveDomain(CognitiveDomain cognitiveDomain) {
        this.cognitiveDomain = cognitiveDomain;
    }

    public GameContentSource getContentSource() {
        return contentSource;
    }

    public void setContentSource(GameContentSource contentSource) {
        this.contentSource = contentSource;
    }

    public PersonalizationType getPersonalizationType() {
        return personalizationType;
    }

    public void setPersonalizationType(PersonalizationType personalizationType) {
        this.personalizationType = personalizationType;
    }

    public Short getMinContentItems() {
        return minContentItems;
    }

    public void setMinContentItems(Short minContentItems) {
        this.minContentItems = minContentItems;
    }

    public short getMinDifficulty() {
        return minDifficulty;
    }

    public void setMinDifficulty(short minDifficulty) {
        this.minDifficulty = minDifficulty;
    }

    public short getMaxDifficulty() {
        return maxDifficulty;
    }

    public void setMaxDifficulty(short maxDifficulty) {
        this.maxDifficulty = maxDifficulty;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Game that)) {
            return false;
        }
        return code != null && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
