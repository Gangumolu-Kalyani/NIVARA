package com.sih.nivara.assistant.tool;

/** Why a tool call failed. Stable names: a model is told them and may react to them. */
public enum ToolErrorCode {
    /** No tool has this name. */
    UNKNOWN_TOOL,
    /** The tool exists, but not for this kind of conversation (patient or caregiver). */
    NOT_ALLOWED,
    /** The arguments are malformed, of the wrong type, unknown, or out of range. */
    INVALID_ARGUMENTS,
    /** A caregiver's conversation about no particular patient must name one. */
    PATIENT_REQUIRED,
    /** The caller may not act on that patient, or the conversation is about another patient. */
    FORBIDDEN,
    /** The patient does not exist, or the caller has no access to it. */
    NOT_FOUND,
    /** The tool changes data and needs the user's confirmation, which is not available yet. */
    CONFIRMATION_REQUIRED,
    /** The model asked for more tool calls in one round than allowed; this one was not run. */
    CALL_LIMIT_EXCEEDED,
    /** Something unexpected went wrong; the details are logged, not returned. */
    INTERNAL_ERROR
}
