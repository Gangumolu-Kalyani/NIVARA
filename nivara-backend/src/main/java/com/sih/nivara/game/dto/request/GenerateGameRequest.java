package com.sih.nivara.game.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to generate a personalized game instance for a patient.
 */
public record GenerateGameRequest(
        @NotBlank
        String gameCode,

        @Min(1)
        short difficulty,

        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String languageCode,

        Short questionCount
) {
}
