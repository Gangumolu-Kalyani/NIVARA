package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.AlertCategory;
import com.sih.nivara.entity.enums.AlertSeverity;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.entity.enums.AlertType;
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
 * Something the care team should look at, such as a reminder the patient did not answer or a
 * request for help. Maps table alerts (V5).
 *
 * <p>An alert starts OPEN and is closed once, as RESOLVED or DISMISSED, by a caregiver.
 */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    /** The reminder occurrence that raised this alert, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reminder_occurrence_id", updatable = false)
    private ReminderOccurrence reminderOccurrence;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30, updatable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private AlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AlertSeverity severity;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private AppUser resolvedByUser;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Alert() {
        // required by JPA
    }

    public Alert(Patient patient, ReminderOccurrence reminderOccurrence, AlertType alertType,
                 AlertCategory category, AlertSeverity severity, String title, String message) {
        this.patient = patient;
        this.reminderOccurrence = reminderOccurrence;
        this.alertType = alertType;
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    /** Closes an open alert as RESOLVED or DISMISSED, recording who closed it and when. */
    public void close(AlertStatus closedAs, AppUser closedBy, Instant at) {
        this.status = closedAs;
        this.resolvedByUser = closedBy;
        this.resolvedAt = at;
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

    public ReminderOccurrence getReminderOccurrence() {
        return reminderOccurrence;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public AppUser getResolvedByUser() {
        return resolvedByUser;
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
        if (!(other instanceof Alert that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
