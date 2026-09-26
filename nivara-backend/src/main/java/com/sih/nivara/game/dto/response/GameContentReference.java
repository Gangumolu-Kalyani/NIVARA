package com.sih.nivara.game.dto.response;

/**
 * Reference to patient data (Person, Place, Object, or Memory) used in a game.
 *
 * <p>Content references help the frontend understand what data is being used without
 * needing to fetch it separately.
 */
public record GameContentReference(
        String subjectType,   // PERSON, PLACE, OBJECT, MEMORY
        String subjectUuid,
        String displayName,
        String extraInfo      // relationship, place type, etc.
) {

    public GameContentReference(String subjectType, String subjectUuid, String displayName,
                                 String extraInfo) {
        this.subjectType = subjectType;
        this.subjectUuid = subjectUuid;
        this.displayName = displayName;
        this.extraInfo = extraInfo;
    }
}
