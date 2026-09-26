package com.sih.nivara.game.dto.response;

/**
 * One option in a multiple-choice question.
 *
 * <p>Options reference patient data (Person, Place, Object, Memory) by UUID.
 */
public record Option(
        String subjectType,  // PERSON, PLACE, OBJECT, MEMORY
        String displayName
) {

    public Option(String subjectType, String displayName) {
        this.subjectType = subjectType;
        this.displayName = displayName;
    }
}
