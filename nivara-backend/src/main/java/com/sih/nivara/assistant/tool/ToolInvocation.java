package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.service.AssistantContext;
import com.sih.nivara.entity.Patient;

import java.time.Instant;

/**
 * One call of a tool, as the tool sees it: the conversation's context, the patient the executor
 * resolved and authorized, the validated arguments, and the instant the call is made.
 */
public record ToolInvocation<A>(
        AssistantContext context,
        Patient patient,
        A arguments,
        Instant now) {
}
