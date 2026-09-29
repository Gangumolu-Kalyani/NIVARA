package com.sih.nivara.assistant.tool;

/**
 * A tool call failed for a reason the caller should be told about. {@link ToolExecutor} turns it
 * into an error {@link ToolResult}; it never reaches the HTTP layer.
 */
public class ToolException extends Exception {

    private final ToolErrorCode code;

    public ToolException(ToolErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ToolErrorCode code() {
        return code;
    }
}
