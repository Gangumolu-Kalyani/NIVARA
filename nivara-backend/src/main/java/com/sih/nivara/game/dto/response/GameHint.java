package com.sih.nivara.game.dto.response;

/**
 * A hint for a question in a generated game.
 *
 * <p>Hints follow a progression:
 * <ul>
 *     <li>Level 0: No hint available (or hint exhausted)</li>
 *     <li>Level 1: Small textual/audio clue</li>
 *     <li>Level 2: Stronger visual/audio clue</li>
 *     <li>Level 3: Explicit assistance</li>
 * </ul>
 */
public record GameHint(
        int level,
        String text,
        HintType type
) {

    /**
     * Creates a hint at the specified level.
     *
     * @param level the hint level (0-3)
     * @param text  the hint text (can be null for level 0)
     * @param type  the hint type
     * @return a GameHint record
     */
    public static GameHint of(int level, String text, HintType type) {
        return new GameHint(level, text, type);
    }

    /**
     * Creates a "no hint" hint (level 0).
     */
    public static GameHint noHint() {
        return new GameHint(0, null, HintType.TEXT);
    }

    /**
     * Is this a meaningful hint (level > 0)?
     */
    public boolean isAvailable() {
        return level > 0;
    }
}
