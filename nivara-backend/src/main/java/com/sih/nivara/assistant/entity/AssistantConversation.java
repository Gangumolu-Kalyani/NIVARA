package com.sih.nivara.assistant.entity;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.entity.enums.ConversationStatus;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
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

import java.time.Instant;
import java.util.UUID;

/**
 * One conversation between an account and the NIVARA assistant. Maps table
 * assistant_conversations (V7).
 *
 * <p>The owner, the patient and the mode are fixed when the conversation starts: a conversation
 * never changes hands or subject. updatedAt is moved explicitly on every new message, so it
 * records the latest activity rather than the latest change to this row.
 */
@Entity
@Table(name = "assistant_conversations")
public class AssistantConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private AppUser owner;

    /** The patient the conversation is about; null for a caregiver's general conversation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", updatable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20, updatable = false)
    private AssistantMode mode;

    @Column(name = "language_code", nullable = false, length = 10, updatable = false)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AssistantConversation() {
        // required by JPA
    }

    public AssistantConversation(AppUser owner, Patient patient, AssistantMode mode, String languageCode) {
        this.owner = owner;
        this.patient = patient;
        this.mode = mode;
        this.languageCode = languageCode;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public void close(Instant at) {
        if (isActive()) {
            this.status = ConversationStatus.CLOSED;
            this.closedAt = at;
            this.updatedAt = at;
        }
    }

    /** Records activity: a message was added. */
    public void touch(Instant at) {
        this.updatedAt = at;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public AppUser getOwner() {
        return owner;
    }

    public Patient getPatient() {
        return patient;
    }

    public AssistantMode getMode() {
        return mode;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
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
        if (!(other instanceof AssistantConversation that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
