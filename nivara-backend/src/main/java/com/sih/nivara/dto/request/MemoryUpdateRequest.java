package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.MemoryType;
import com.sih.nivara.entity.enums.TimeOfDay;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Body of "correct a memory": a full replacement of the editable fields, so an omitted place
 * clears the place and an omitted collection clears those links.
 *
 * <p>Two fields of table memories are deliberately absent. The patient cannot change, because
 * memories.patient_id is immutable and the memory is addressed within one patient. The source
 * cannot change either: it records where the memory came from when it was first captured.
 *
 * <p>includeInGames is required here because the column is NOT NULL and a replacement cannot
 * leave it unstated.
 */
public record MemoryUpdateRequest(

        @NotBlank
        @Size(max = 150)
        String title,

        @NotBlank
        String description,

        @NotNull
        MemoryType memoryType,

        @NotNull
        @PastOrPresent
        LocalDate occurredOn,

        TimeOfDay timeOfDay,

        UUID placeUuid,

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String languageCode,

        @NotNull
        Boolean includeInGames,

        Set<UUID> peopleUuids,

        Set<UUID> objectUuids) {
}
