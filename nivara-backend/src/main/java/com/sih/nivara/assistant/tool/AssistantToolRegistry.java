package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Every assistant tool, by name. Built once from the {@link AssistantTool} beans; a duplicate or
 * malformed name stops the application from starting rather than letting one tool shadow another.
 */
@Component
public class AssistantToolRegistry {

    static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** The argument the executor adds in caregiver conversations to name the patient. */
    public static final String PATIENT_UUID = "patientUuid";

    private final Map<String, AssistantTool<?>> toolsByName;

    public AssistantToolRegistry(List<AssistantTool<?>> tools) {
        Map<String, AssistantTool<?>> byName = new TreeMap<>();
        for (AssistantTool<?> tool : tools) {
            String name = tool.name();
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalStateException("Assistant tool " + tool.getClass().getName()
                        + " has an invalid name '" + name + "'; names match " + NAME.pattern());
            }
            if (tool.description() == null || tool.description().isBlank()) {
                throw new IllegalStateException("Assistant tool '" + name + "' has no description");
            }
            if (tool.modes() == null || tool.modes().isEmpty()) {
                throw new IllegalStateException("Assistant tool '" + name + "' is available in no mode");
            }
            if (tool.argumentProperties().containsKey(PATIENT_UUID)) {
                throw new IllegalStateException("Assistant tool '" + name + "' declares " + PATIENT_UUID
                        + ", which only the executor may define");
            }
            AssistantTool<?> previous = byName.putIfAbsent(name, tool);
            if (previous != null) {
                throw new IllegalStateException("Two assistant tools are named '" + name + "': "
                        + previous.getClass().getName() + " and " + tool.getClass().getName());
            }
        }
        this.toolsByName = Map.copyOf(byName);
    }

    public Optional<AssistantTool<?>> find(String name) {
        return Optional.ofNullable(name).map(toolsByName::get);
    }

    /** Every tool, by name. */
    public List<AssistantTool<?>> all() {
        return toolsByName.values().stream()
                .sorted(Comparator.comparing(AssistantTool::name))
                .toList();
    }

    /** The tools a conversation of this mode may use, as a model is shown them, by name. */
    public List<ToolDefinition> toolsFor(AssistantMode mode) {
        return all().stream()
                .filter(tool -> tool.modes().contains(mode))
                .map(tool -> definitionOf(tool, mode))
                .toList();
    }

    /**
     * The tool's definition for this mode. Caregiver conversations get an optional patientUuid;
     * patient conversations never do, because a patient only ever reaches themselves.
     */
    public static ToolDefinition definitionOf(AssistantTool<?> tool, AssistantMode mode) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (mode == AssistantMode.CAREGIVER) {
            properties.put(PATIENT_UUID, JsonSchema.uuid(
                    "The patient this is about. Optional when the conversation is already about a patient."));
        }
        properties.putAll(tool.argumentProperties());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(tool.requiredArguments()));
        schema.put("additionalProperties", false);
        return new ToolDefinition(tool.name(), tool.description(), schema, tool.requiresConfirmation());
    }
}
