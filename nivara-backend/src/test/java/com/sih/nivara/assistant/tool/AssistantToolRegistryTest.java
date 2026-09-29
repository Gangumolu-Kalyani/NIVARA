package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.entity.enums.AccessLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry on its own, with stand-in tools: no Spring, no database. */
class AssistantToolRegistryTest {

    /** A minimal tool with a configurable name, modes, arguments and confirmation flag. */
    private record FakeTool(String name, Set<AssistantMode> modes, Map<String, Object> argumentProperties,
                            boolean requiresConfirmation) implements AssistantTool<NoArguments> {

        FakeTool(String name, AssistantMode... modes) {
            this(name, Set.of(modes), Map.of("query", JsonSchema.string("Words", 50)), false);
        }

        @Override
        public String description() {
            return "A test tool";
        }

        @Override
        public Class<NoArguments> argumentsType() {
            return NoArguments.class;
        }

        @Override
        public List<String> requiredArguments() {
            return List.of("query");
        }

        @Override
        public AccessLevel requiredAccess() {
            return AccessLevel.VIEWER;
        }

        @Override
        public Object execute(ToolInvocation<NoArguments> invocation) {
            return "done";
        }
    }

    @Test
    void findsRegisteredToolsByName() {
        FakeTool both = new FakeTool("look_up", AssistantMode.PATIENT, AssistantMode.CAREGIVER);
        FakeTool caregiverOnly = new FakeTool("care_only", AssistantMode.CAREGIVER);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(both, caregiverOnly));

        assertSame(both, registry.find("look_up").orElseThrow());
        assertSame(caregiverOnly, registry.find("care_only").orElseThrow());
        assertEquals(List.of("care_only", "look_up"), registry.all().stream().map(AssistantTool::name).toList());
    }

    @Test
    void unknownNamesAreNotFound() {
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(new FakeTool("look_up", AssistantMode.PATIENT)));

        assertTrue(registry.find("delete_everything").isEmpty());
        assertTrue(registry.find("LOOK_UP").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void duplicateNamesAreRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new AssistantToolRegistry(List.of(
                new FakeTool("look_up", AssistantMode.PATIENT), new FakeTool("look_up", AssistantMode.CAREGIVER))));
        assertTrue(e.getMessage().contains("Two assistant tools are named 'look_up'"), e.getMessage());
    }

    @Test
    void malformedToolsAreRejected() {
        for (String bad : new String[] {"LookUp", "look-up", "1look", "", "a".repeat(65)}) {
            assertThrows(IllegalStateException.class,
                    () -> new AssistantToolRegistry(List.of(new FakeTool(bad, AssistantMode.PATIENT))), bad);
        }
        assertThrows(IllegalStateException.class, () -> new AssistantToolRegistry(List.of(new FakeTool("no_modes"))));
        assertThrows(IllegalStateException.class, () -> new AssistantToolRegistry(List.of(new FakeTool("claims_patient",
                Set.of(AssistantMode.CAREGIVER), Map.of(AssistantToolRegistry.PATIENT_UUID, Map.of()), false))));
    }

    @Test
    void definitionsDependOnTheConversationMode() {
        FakeTool both = new FakeTool("look_up", AssistantMode.PATIENT, AssistantMode.CAREGIVER);
        FakeTool caregiverOnly = new FakeTool("care_only", Set.of(AssistantMode.CAREGIVER),
                Map.of("query", JsonSchema.string("Words", 50)), true);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(both, caregiverOnly));

        List<ToolDefinition> patient = registry.toolsFor(AssistantMode.PATIENT);
        assertEquals(List.of("look_up"), patient.stream().map(ToolDefinition::name).toList());
        Map<?, ?> patientProperties = (Map<?, ?>) patient.get(0).parametersSchema().get("properties");
        assertFalse(patientProperties.containsKey(AssistantToolRegistry.PATIENT_UUID),
                "a patient's tools never take a patient");
        assertTrue(patientProperties.containsKey("query"));

        List<ToolDefinition> caregiver = registry.toolsFor(AssistantMode.CAREGIVER);
        assertEquals(List.of("care_only", "look_up"), caregiver.stream().map(ToolDefinition::name).toList());
        Map<String, Object> schema = caregiver.get(1).parametersSchema();
        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
        assertEquals(List.of("query"), schema.get("required"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey(AssistantToolRegistry.PATIENT_UUID));

        // Confirmation metadata is carried into the definition
        assertTrue(caregiver.get(0).requiresConfirmation());
        assertFalse(caregiver.get(1).requiresConfirmation());
    }
}
