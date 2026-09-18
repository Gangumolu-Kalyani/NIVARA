package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.MemorySource;
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
 * Body of "record a memory for this patient". The patient comes from the request path, and
 * the account that recorded it from the caller, so neither appears here.
 *
 * <p>Bounds mirror table memories (V2): the column lengths and the CHECK constraints
 * ck_memories_title_not_blank, ck_memories_description_not_blank and ck_memories_language_code.
 * The lower bound ck_memories_occurred_on (1900-01-01) has no Bean Validation equivalent and
 * stays with the database; {@code @PastOrPresent} is the domain rule that a memory already
 * happened, which the schema itself does not express.
 *
 * <p>The place, people and objects are named by their public uuids and must belong to the same
 * patient. A null or missing collection means "none".
 */
public record MemoryCreateRequest(

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
        MemorySource source,

        Boolean includeInGames,

        Set<UUID> peopleUuids,

        Set<UUID> objectUuids) {
}
