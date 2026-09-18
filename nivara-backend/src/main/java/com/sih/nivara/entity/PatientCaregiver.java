package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.AccessLevel;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Which caregiver may access which patient. Maps table patient_caregivers (V1).
 *
 * <p>This is an explicit entity rather than a &#64;ManyToMany because the link carries its own
 * attributes and is the project's authorization boundary. Access must be granted, changed and
 * revoked deliberately, never as a side effect of collection manipulation.
 *
 * <p>This class holds no authorization logic; the service and security layers read it.
 * The database also enforces at most one primary caregiver per patient through a partial
 * unique index that JPA cannot express.
 */
@Entity
@Table(name = "patient_caregivers")
public class PatientCaregiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_user_id", nullable = false, updatable = false)
    private AppUser caregiverUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private RelationshipType relationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 10)
    private AccessLevel accessLevel = AccessLevel.EDITOR;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "receives_alerts", nullable = false)
    private boolean receivesAlerts = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientCaregiver() {
        // required by JPA
    }

    public PatientCaregiver(Patient patient, AppUser caregiverUser, RelationshipType relationship) {
        this.patient = patient;
        this.caregiverUser = caregiverUser;
        this.relationship = relationship;
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public AppUser getCaregiverUser() {
        return caregiverUser;
    }

    public RelationshipType getRelationship() {
        return relationship;
    }

    public void setRelationship(RelationshipType relationship) {
        this.relationship = relationship;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public boolean isReceivesAlerts() {
        return receivesAlerts;
    }

    public void setReceivesAlerts(boolean receivesAlerts) {
        this.receivesAlerts = receivesAlerts;
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
        if (!(other instanceof PatientCaregiver that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
