package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.entity.enums.AccessLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Something the assistant can do on the user's behalf, backed by an existing NIVARA service.
 *
 * <p>A tool never decides whose data it touches. {@link ToolExecutor} works out the patient from
 * the authenticated caller, checks access, reads and validates the arguments, and only then calls
 * {@link #execute} with an already authorized patient. A tool therefore has no patient argument of
 * its own; in caregiver mode the executor adds an optional {@code patientUuid} to the schema and
 * checks it with PatientAccessService.
 *
 * <p>Implementations are Spring beans; {@link AssistantToolRegistry} collects them.
 *
 * @param <A> the tool's arguments: a record, validated with Bean Validation annotations
 */
public interface AssistantTool<A> {

    /** Unique, snake_case: {@code ^[a-z][a-z0-9_]{0,63}$}. */
    String name();

    /** What the tool does, written for a language model deciding whether to call it. */
    String description();

    /** The type the JSON arguments are read into. Unknown properties are rejected. */
    Class<A> argumentsType();

    /** JSON Schema of each argument, by name, excluding patientUuid, which the executor owns. */
    Map<String, Object> argumentProperties();

    /** Arguments that must be present. */
    default List<String> requiredArguments() {
        return List.of();
    }

    /** Which kinds of conversation may use the tool. */
    Set<AssistantMode> modes();

    /** The care-team level a caregiver needs for the patient. Patients always reach only themselves. */
    AccessLevel requiredAccess();

    /**
     * Whether the user must confirm before the tool runs. No Phase 3 tool changes data, so all of
     * them answer false; tools that write will answer true.
     */
    boolean requiresConfirmation();

    /**
     * Does the work and answers the result, a response DTO or record that serializes to JSON.
     * Throws {@link ToolException} for a failure the caller should be told about.
     */
    Object execute(ToolInvocation<A> invocation) throws ToolException;
}
