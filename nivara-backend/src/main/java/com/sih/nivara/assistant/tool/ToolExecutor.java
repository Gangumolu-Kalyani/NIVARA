package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.service.AssistantContext;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.security.CurrentPatientProvider;
import com.sih.nivara.service.PatientAccessService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs assistant tools, and is the only place that decides whether a tool call may run.
 *
 * <p>For every call, in order:
 * <ol>
 *   <li>the tool must exist ({@code UNKNOWN_TOOL}) and serve this kind of conversation
 *       ({@code NOT_ALLOWED});</li>
 *   <li>the arguments are read into the tool's argument type, rejecting unknown properties, and
 *       validated ({@code INVALID_ARGUMENTS});</li>
 *   <li>the patient is resolved from the authenticated caller, never trusted from the arguments:
 *       a patient always gets their own record through {@link CurrentPatientProvider}; a caregiver
 *       gets the named or the conversation's patient, checked with {@link PatientAccessService} on
 *       every call ({@code PATIENT_REQUIRED}, {@code FORBIDDEN}, {@code NOT_FOUND});</li>
 *   <li>only then does the tool run.</li>
 * </ol>
 *
 * <p>Expected failures come back as an error {@link ToolResult}, never as an exception, so a model
 * can be told what went wrong. Unexpected ones become {@code INTERNAL_ERROR} with a generic message;
 * the details go to the log only.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final AssistantToolRegistry registry;
    private final CurrentPatientProvider currentPatientProvider;
    private final PatientAccessService patientAccessService;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public ToolExecutor(AssistantToolRegistry registry,
                        CurrentPatientProvider currentPatientProvider,
                        PatientAccessService patientAccessService,
                        JsonMapper jsonMapper,
                        Validator validator) {
        this.registry = registry;
        this.currentPatientProvider = currentPatientProvider;
        this.patientAccessService = patientAccessService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /**
     * Runs the named tool for this conversation. arguments may be null or empty for a tool that
     * needs none.
     */
    public ToolResult execute(AssistantContext context, String toolName, JsonNode arguments) {
        AssistantTool<?> tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            return ToolResult.error(toolName, ToolErrorCode.UNKNOWN_TOOL, "There is no tool named '" + toolName + "'");
        }
        try {
            return run(tool, context, arguments);
        } catch (ToolException e) {
            return ToolResult.error(tool.name(), e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Assistant tool {} failed", tool.name(), e);
            return ToolResult.error(tool.name(), ToolErrorCode.INTERNAL_ERROR, "The tool could not complete");
        }
    }

    private <A> ToolResult run(AssistantTool<A> tool, AssistantContext context, JsonNode arguments)
            throws ToolException {
        if (!tool.modes().contains(context.mode())) {
            throw new ToolException(ToolErrorCode.NOT_ALLOWED,
                    "The tool '" + tool.name() + "' is not available in a " + context.mode().name().toLowerCase()
                            + " conversation");
        }

        ObjectNode object = argumentsObject(arguments);
        UUID requestedPatient = takePatientUuid(object);
        A parsed = bind(tool.argumentsType(), object);
        validate(parsed);

        Patient patient = resolvePatient(tool, context, requestedPatient);
        try {
            return ToolResult.success(tool.name(), tool.execute(new ToolInvocation<>(context, patient, parsed, Instant.now())));
        } catch (ResponseStatusException e) {
            throw fromStatus(e);
        }
    }

    // ---- arguments ---------------------------------------------------------------------------

    private ObjectNode argumentsObject(JsonNode arguments) throws ToolException {
        if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
            return jsonMapper.createObjectNode();
        }
        if (!arguments.isObject()) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, "Arguments must be a JSON object");
        }
        return ((ObjectNode) arguments).deepCopy();
    }

    /** Removes patientUuid from the arguments, which the tool itself never sees, and parses it. */
    private static UUID takePatientUuid(ObjectNode object) throws ToolException {
        JsonNode value = object.remove(AssistantToolRegistry.PATIENT_UUID);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            if (!value.isString()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException e) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, AssistantToolRegistry.PATIENT_UUID + ": must be a uuid");
        }
    }

    private <A> A bind(Class<A> type, ObjectNode object) throws ToolException {
        try {
            return jsonMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(object);
        } catch (UnrecognizedPropertyException e) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, "Unknown argument '" + e.getPropertyName() + "'");
        } catch (DatabindException e) {
            String field = e.getPath().stream()
                    .map(reference -> reference.getPropertyName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS,
                    (field.isEmpty() ? "Arguments" : field) + ": has an invalid value");
        } catch (JacksonException e) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, "Arguments could not be read");
        }
    }

    private void validate(Object arguments) throws ToolException {
        Set<ConstraintViolation<Object>> violations = validator.validate(arguments);
        if (!violations.isEmpty()) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
    }

    // ---- patient -----------------------------------------------------------------------------

    private Patient resolvePatient(AssistantTool<?> tool, AssistantContext context, UUID requested)
            throws ToolException {
        if (context.mode() == AssistantMode.PATIENT) {
            return ownPatient(context, requested);
        }
        return caregiversPatient(tool, context, requested);
    }

    /**
     * A patient's own record, looked up again from the authenticated account. A patient may only
     * name themselves.
     */
    private Patient ownPatient(AssistantContext context, UUID requested) throws ToolException {
        Patient own = currentPatientProvider.currentPatient()
                .orElseThrow(() -> new ToolException(ToolErrorCode.FORBIDDEN, "Only a patient's own device can do this"));
        if (context.patient() == null || !own.getId().equals(context.patient().getId())) {
            throw new ToolException(ToolErrorCode.FORBIDDEN, "This conversation belongs to another patient");
        }
        if (requested != null && !requested.equals(own.getUuid())) {
            throw new ToolException(ToolErrorCode.FORBIDDEN, "A patient can only look at their own information");
        }
        return own;
    }

    /**
     * The patient a caregiver's call is about: the one named, or else the conversation's. A
     * conversation about one patient stays about that patient. Access is checked on every call.
     */
    private Patient caregiversPatient(AssistantTool<?> tool, AssistantContext context, UUID requested)
            throws ToolException {
        Patient pinned = context.patient();
        UUID target = requested != null ? requested : pinned == null ? null : pinned.getUuid();
        if (target == null) {
            throw new ToolException(ToolErrorCode.PATIENT_REQUIRED,
                    "Say which patient this is about: " + AssistantToolRegistry.PATIENT_UUID + " is required");
        }
        if (pinned != null && !target.equals(pinned.getUuid())) {
            throw new ToolException(ToolErrorCode.FORBIDDEN, "This conversation is about another patient");
        }
        try {
            return patientAccessService.requirePatient(target, tool.requiredAccess());
        } catch (ResponseStatusException e) {
            throw fromStatus(e);
        }
    }

    private static ToolException fromStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        ToolErrorCode code = status == null ? ToolErrorCode.INTERNAL_ERROR : switch (status) {
            case NOT_FOUND -> ToolErrorCode.NOT_FOUND;
            case FORBIDDEN -> ToolErrorCode.FORBIDDEN;
            case BAD_REQUEST -> ToolErrorCode.INVALID_ARGUMENTS;
            default -> ToolErrorCode.INTERNAL_ERROR;
        };
        String message = code == ToolErrorCode.INTERNAL_ERROR ? "The tool could not complete" : e.getReason();
        return new ToolException(code, message);
    }
}
