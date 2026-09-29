package com.sih.nivara.assistant.tool;

import java.util.Map;

/**
 * A tool as a model is shown it, for one kind of conversation: its name, what it does, the JSON
 * Schema of its arguments, and whether the user must confirm it first.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema,
        boolean requiresConfirmation) {
}
