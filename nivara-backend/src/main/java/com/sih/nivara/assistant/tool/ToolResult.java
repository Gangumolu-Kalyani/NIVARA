package com.sih.nivara.assistant.tool;

/**
 * The outcome of one tool call, always in the same shape, so a model can read it:
 *
 * <pre>
 * { "tool": "get_people", "status": "SUCCESS", "data": { ... }, "error": null }
 * { "tool": "get_people", "status": "ERROR", "data": null,
 *   "error": { "code": "INVALID_ARGUMENTS", "message": "limit: must be less than or equal to 50" } }
 * </pre>
 */
public record ToolResult(
        String tool,
        Status status,
        Object data,
        Error error) {

    public enum Status {
        SUCCESS,
        ERROR
    }

    public record Error(ToolErrorCode code, String message) {
    }

    public static ToolResult success(String tool, Object data) {
        return new ToolResult(tool, Status.SUCCESS, data, null);
    }

    public static ToolResult error(String tool, ToolErrorCode code, String message) {
        return new ToolResult(tool, Status.ERROR, null, new Error(code, message));
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
