package com.sih.nivara.assistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of "say something to the assistant". Leading and trailing whitespace is dropped. */
public record MessageRequest(

        @NotBlank
        @Size(max = 4000)
        String content) {
}
