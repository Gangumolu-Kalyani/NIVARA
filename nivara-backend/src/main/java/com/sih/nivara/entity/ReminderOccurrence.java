package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.PatientResponseType;
import com.sih.nivara.entity.enums.ReminderResponseStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One scheduled instance of a {@link Reminder}, and what happened to it. Maps table
 * reminder_occurrences (V5).
 *
 * <p>Rows are created by {@code ReminderOccurrenceRepository.insertIfAbsent}, not through this
 * class's constructor, so that creating the same slot twice is harmless. From then on the
 * lifecycle is: PENDING until due; SENT once the patient has been nudged; SEEN after "remind me
 * later"; COMPLETED once taken; ESCALATED when the care team was alerted; MISSED when the day
 * ended without an answer. A patient can still answer TAKEN after an escalation.
 */
@Entity
@Table(name = "reminder_occurrences")
public class ReminderOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false, updatable = false)
    private Reminder reminder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private ReminderResponseStatus responseStatus = ReminderResponseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", length = 20)
    private PatientResponseType responseType;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "nudge_count", nullable = false)
    private short nudgeCount = 0;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ReminderOccurrence() {
        // required by JPA; rows are inserted by the repository
    }

    /** Still waiting for an answer: not completed, missed or escalated. */
    public boolean isAwaitingResponse() {
        return responseStatus == ReminderResponseStatus.PENDING
                || responseStatus == ReminderResponseStatus.SENT
                || responseStatus == ReminderResponseStatus.SEEN;
    }

    /** Records the patient's answer. */
    public void respond(PatientResponseType type, Instant at) {
        this.responseType = type;
        this.respondedAt = at;
    }

    /** Nudges the patient once more. */
    public void nudge(Instant at) {
        this.notifiedAt = at;
        this.nudgeCount++;
        if (responseStatus == ReminderResponseStatus.PENDING) {
            this.responseStatus = ReminderResponseStatus.SENT;
        }
    }

    public void escalate(Instant at) {
        this.responseStatus = ReminderResponseStatus.ESCALATED;
        this.escalatedAt = at;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Reminder getReminder() {
        return reminder;
    }

    public Patient getPatient() {
        return patient;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public ReminderResponseStatus getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(ReminderResponseStatus responseStatus) {
        this.responseStatus = responseStatus;
    }

    public PatientResponseType getResponseType() {
        return responseType;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public short getNudgeCount() {
        return nudgeCount;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public boolean isEscalated() {
        return escalatedAt != null;
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
        if (!(other instanceof ReminderOccurrence that)) {
            return false;
        }
        return uuid != null && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return ReminderOccurrence.class.hashCode();
    }
}
