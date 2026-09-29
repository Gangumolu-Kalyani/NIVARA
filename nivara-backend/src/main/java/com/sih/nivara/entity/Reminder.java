package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.DayOfWeekCode;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.entity.enums.ReminderRepeatType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Daily Assistance reminder: what the patient should be reminded of, and on which days at what
 * time. Maps table reminders (V5).
 *
 * <p>scheduledTime is a wall-clock time in the patient's timezone. Each time the reminder falls
 * due, a {@link ReminderOccurrence} records what happened.
 *
 * <p>A reminder is never physically deleted, because its occurrences keep a history: deleting sets
 * deletedAt and deactivates it.
 */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ReminderCategory category;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 10)
    private ReminderRepeatType repeatType = ReminderRepeatType.DAILY;

    /** Only for WEEKLY reminders; empty otherwise (stored as NULL). */
    @Convert(converter = DayOfWeekCodesConverter.class)
    @Column(name = "repeat_days", length = 27)
    private Set<DayOfWeekCode> repeatDays = EnumSet.noneOf(DayOfWeekCode.class);

    /** Only for ONCE reminders. */
    @Column(name = "one_off_date")
    private LocalDate oneOffDate;

    @Column(name = "escalate_after_missed", nullable = false)
    private short escalateAfterMissed = 2;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** When the current schedule took effect; no occurrence is created before it. */
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom = Instant.now();

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

    protected Reminder() {
        // required by JPA
    }

    public Reminder(Patient patient, ReminderCategory category, String title, LocalTime scheduledTime,
                    AppUser createdByUser) {
        this.patient = patient;
        this.category = category;
        this.title = title;
        this.scheduledTime = scheduledTime;
        this.createdByUser = createdByUser;
    }

    /** Whether the reminder can still fall due: active and not deleted. */
    public boolean isLive() {
        return active && deletedAt == null;
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

    public ReminderCategory getCategory() {
        return category;
    }

    public void setCategory(ReminderCategory category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public LocalTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public ReminderRepeatType getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(ReminderRepeatType repeatType) {
        this.repeatType = repeatType;
    }

    public Set<DayOfWeekCode> getRepeatDays() {
        return repeatDays;
    }

    public void setRepeatDays(Set<DayOfWeekCode> repeatDays) {
        this.repeatDays = repeatDays == null || repeatDays.isEmpty()
                ? EnumSet.noneOf(DayOfWeekCode.class)
                : EnumSet.copyOf(repeatDays);
    }

    public LocalDate getOneOffDate() {
        return oneOffDate;
    }

    public void setOneOffDate(LocalDate oneOffDate) {
        this.oneOffDate = oneOffDate;
    }

    public short getEscalateAfterMissed() {
        return escalateAfterMissed;
    }

    public void setEscalateAfterMissed(short escalateAfterMissed) {
        this.escalateAfterMissed = escalateAfterMissed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
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
        if (!(other instanceof Reminder that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
