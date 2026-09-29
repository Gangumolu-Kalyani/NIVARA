package com.sih.nivara.game.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;

/**
 * Metadata about a generated game instance.
 */
public record GameMetadata(
        CognitiveDomain domain,
        int totalQuestions,
        int minQuestionsForDifficulty,
        int maxQuestionsForDifficulty
) {
}
