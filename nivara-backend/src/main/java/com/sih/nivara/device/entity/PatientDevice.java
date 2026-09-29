package com.sih.nivara.device.entity;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A device a patient uses, paired by a caregiver. Maps table patient_devices (V6).
 *
 * <p>Lifecycle: a caregiver creates the row with a one-time pairing code (PENDING). The patient's
 * device redeems the code and receives a device secret (ACTIVE). A caregiver can revoke it at any
 * time (REVOKED). A code nobody redeemed in time is EXPIRED. Only hashes of the code and of the
 * secret are stored; the plaintext values are shown once and never again.
 */
@Entity
@Table(name = "patient_devices")
public class PatientDevice {

    /** What a device can currently do; derived from its columns, not stored. */
    public enum Status {
        PENDING,
        ACTIVE,
        EXPIRED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "label", length = 60)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AppUser createdByUser;

    @Column(name = "pairing_code_hash", length = 64)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private Instant pairingExpiresAt;

    @Column(name = "secret_hash", length = 64)
    private String secretHash;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PatientDevice() {
        // required by JPA
    }

    /** A device waiting to be paired with the code whose hash is given. */
    public PatientDevice(Patient patient, String label, AppUser createdByUser,
                         String pairingCodeHash, Instant pairingExpiresAt) {
        this.patient = patient;
        this.label = label;
        this.createdByUser = createdByUser;
        this.pairingCodeHash = pairingCodeHash;
        this.pairingExpiresAt = pairingExpiresAt;
    }

    public Status statusAt(Instant now) {
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        if (pairedAt != null) {
            return Status.ACTIVE;
        }
        return now.isBefore(pairingExpiresAt) ? Status.PENDING : Status.EXPIRED;
    }

    /** Redeems the pairing code: the code is forgotten and the device secret takes its place. */
    public void pair(String secretHash, Instant at) {
        this.pairingCodeHash = null;
        this.pairingExpiresAt = null;
        this.secretHash = secretHash;
        this.pairedAt = at;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }

    public void markUsed(Instant at) {
        this.lastUsedAt = at;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getLabel() {
        return label;
    }

    public AppUser getCreatedByUser() {
        return createdByUser;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public Instant getPairingExpiresAt() {
        return pairingExpiresAt;
    }

    public Instant getPairedAt() {
        return pairedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
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
        if (!(other instanceof PatientDevice that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
