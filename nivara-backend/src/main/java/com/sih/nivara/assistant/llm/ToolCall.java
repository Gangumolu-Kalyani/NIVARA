package com.sih.nivara.assistant.llm;

/**
 * A model asking the backend to run a tool. argumentsJson is exactly what the model sent, a JSON
 * object as text; the backend parses and validates it, and never trusts it.
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
