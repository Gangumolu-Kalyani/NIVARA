package com.sih.nivara.game.dto.response;

import java.util.List;

/**
 * One question in a generated game instance.
 *
 * <p>Questions contain all information needed for the frontend to render and validate answers.
 */
public record GeneratedQuestion(
        short questionNumber,
        String questionType,
        String questionText,
        String languageCode,
        GameHint initialHint,
        List<Option> options
) {

    public GeneratedQuestion(short questionNumber, String questionType, String questionText,
                             String languageCode, GameHint initialHint, List<Option> options) {
        this.questionNumber = questionNumber;
        this.questionType = questionType;
        this.questionText = questionText;
        this.languageCode = languageCode;
        this.initialHint = initialHint != null ? initialHint : GameHint.noHint();
        this.options = options != null ? options : List.of();
    }
}
