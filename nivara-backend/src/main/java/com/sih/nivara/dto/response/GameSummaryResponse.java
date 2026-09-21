package com.sih.nivara.dto.response;

/**
 * Enough of a game to show which one an attempt was, embedded in game results. The full game
 * is {@link GameResponse}.
 */
public record GameSummaryResponse(
        String code,
        String name) {
}
