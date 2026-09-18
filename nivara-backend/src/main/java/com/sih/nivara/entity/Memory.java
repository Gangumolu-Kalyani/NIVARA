package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.MemorySource;
import com.sih.nivara.entity.enums.MemoryType;
import com.sih.nivara.entity.enums.TimeOfDay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Something that happened to the patient. Events and daily activities are memoryType values,
 * so there is no separate event table. Maps table memories (V2).
 *
 * <p>A memory happens at one optional place (memories.place_id); there is no memory_places table.
 * People and objects involved are many-to-many through memory_people and memory_objects.
 */
@Entity
@Table(name = "memories")
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    /** A full sentence the voice assistant can read aloud. */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", nullable = false, length = 30)
    private MemoryType memoryType;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_of_day", length = 10)
    private TimeOfDay timeOfDay;

    /** Optional place; no cascade, because a place exists independently of any memory. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MemorySource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private AppUser recordedByUser;

    @Column(name = "include_in_games", nullable = false)
    private boolean includeInGames = true;

    /**
     * Who was involved. A Set, not a List, so removing one link deletes only that row.
     * No cascade: people have their own lifecycle and are protected by ON DELETE RESTRICT.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "memory_people",
            joinColumns = @JoinColumn(name = "memory_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    private Set<Person> people = new LinkedHashSet<>();

    /** Which objects were involved. Same mapping rules as {@link #people}. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "memory_objects",
            joinColumns = @JoinColumn(name = "memory_id"),
            inverseJoinColumns = @JoinColumn(name = "object_id"))
    private Set<PersonalObject> objects = new LinkedHashSet<>();

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

    protected Memory() {
        // required by JPA
    }

    public Memory(Patient patient, String title, String description, MemoryType memoryType,
                  LocalDate occurredOn, String languageCode, MemorySource source) {
        this.patient = patient;
        this.title = title;
        this.description = description;
        this.memoryType = memoryType;
        this.occurredOn = occurredOn;
        this.languageCode = languageCode;
        this.source = source;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MemoryType getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(MemoryType memoryType) {
        this.memoryType = memoryType;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public void setOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place place) {
        this.place = place;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public MemorySource getSource() {
        return source;
    }

    public void setSource(MemorySource source) {
        this.source = source;
    }

    public AppUser getRecordedByUser() {
        return recordedByUser;
    }

    public void setRecordedByUser(AppUser recordedByUser) {
        this.recordedByUser = recordedByUser;
    }

    public boolean isIncludeInGames() {
        return includeInGames;
    }

    public void setIncludeInGames(boolean includeInGames) {
        this.includeInGames = includeInGames;
    }

    public Set<Person> getPeople() {
        return people;
    }

    public void addPerson(Person person) {
        people.add(person);
    }

    public void removePerson(Person person) {
        people.remove(person);
    }

    public Set<PersonalObject> getObjects() {
        return objects;
    }

    public void addObject(PersonalObject personalObject) {
        objects.add(personalObject);
    }

    public void removeObject(PersonalObject personalObject) {
        objects.remove(personalObject);
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
        if (!(other instanceof Memory that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
