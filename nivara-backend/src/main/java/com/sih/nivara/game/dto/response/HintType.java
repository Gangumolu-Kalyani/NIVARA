package com.sih.nivara.game.dto.response;

/**
 * The type of hint content.
 */
public enum HintType {
    /**
     * Text-based hint (most common for all games).
     */
    TEXT,

    /**
     * Audio-based hint (for speech/memory games).
     */
    AUDIO,

    /**
     * Visual hint (partial reveal, icons, etc.).
     */
    VISUAL
}
