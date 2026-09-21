package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.PlaceCreateRequest;
import com.sih.nivara.dto.request.PlaceUpdateRequest;
import com.sih.nivara.dto.response.PlaceResponse;
import com.sih.nivara.dto.response.PlaceSummaryResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Place;

/**
 * Explicit conversion between the places DTOs and {@link Place}.
 *
 * <p>Pure field copying: no repository, no service, no rule. The patient and the creating
 * account are resolved by the caller and passed in.
 *
 * <p>{@link #toResponse} reads the lazy patient association, so the place must have been
 * loaded with it fetched or be mapped inside the transaction that loaded it.
 */
public final class PlaceMapper {

    private PlaceMapper() {
        // utility class
    }

    /** Builds a new place. An omitted includeInGames keeps the default of true. */
    public static Place toEntity(PlaceCreateRequest request, Patient patient, AppUser createdByUser) {
        Place place = new Place(patient, request.name(), request.placeType(), createdByUser);
        place.setDescription(request.description());
        if (request.includeInGames() != null) {
            place.setIncludeInGames(request.includeInGames());
        }
        return place;
    }

    /** Copies a full-replacement update onto an existing place. */
    public static void applyUpdate(PlaceUpdateRequest request, Place place) {
        place.setName(request.name());
        place.setPlaceType(request.placeType());
        place.setDescription(request.description());
        place.setIncludeInGames(request.includeInGames());
    }

    public static PlaceResponse toResponse(Place place) {
        return new PlaceResponse(
                place.getUuid(),
                place.getPatient().getUuid(),
                place.getName(),
                place.getPlaceType(),
                place.getDescription(),
                place.isIncludeInGames(),
                place.getCreatedAt(),
                place.getUpdatedAt());
    }

    /** The short form a memory embeds for the place it happened at. */
    public static PlaceSummaryResponse toSummary(Place place) {
        return new PlaceSummaryResponse(
                place.getUuid(),
                place.getName(),
                place.getPlaceType());
    }
}
