package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.JsonSchema;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.mapper.PersonMapper;
import com.sih.nivara.dto.response.PersonResponse;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;
import com.sih.nivara.service.PersonService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The people in the patient's life, by name, from {@link PersonService#findByPatient}; optionally
 * only those whose name matches or who have a given relationship. Answers "Who is Anil?".
 */
@Component
public class GetPeopleTool implements AssistantTool<GetPeopleTool.Arguments> {

    static final int MAX_LIMIT = 50;

    public record Arguments(
            @Size(max = 120) String name,
            RelationshipType relationship,
            @Min(1) @Max(MAX_LIMIT) Integer limit) {
    }

    /** totalMatching counts every matching person, not only the ones returned. */
    public record Result(List<PersonResponse> people, int totalMatching) {
    }

    private final PersonService personService;

    public GetPeopleTool(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public String name() {
        return "get_people";
    }

    @Override
    public String description() {
        return "Lists the people in the patient's life (family, friends, neighbours, carers, doctors) with how "
                + "they are related and a short description. Filter by part of a name or by relationship.";
    }

    @Override
    public Class<Arguments> argumentsType() {
        return Arguments.class;
    }

    @Override
    public Map<String, Object> argumentProperties() {
        return JsonSchema.ordered(
                "name", JsonSchema.string("Part of the person's name, nickname or relationship label; any case.", 120),
                "relationship", JsonSchema.oneOf("Only people with this relationship to the patient.",
                        RelationshipType.values()),
                "limit", JsonSchema.integer("How many people to return. Default " + MAX_LIMIT + ".", 1, MAX_LIMIT));
    }

    @Override
    public Set<AssistantMode> modes() {
        return Set.of(AssistantMode.PATIENT, AssistantMode.CAREGIVER);
    }

    @Override
    public AccessLevel requiredAccess() {
        return AccessLevel.VIEWER;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public Result execute(ToolInvocation<Arguments> invocation) {
        Arguments arguments = invocation.arguments();
        String needle = arguments.name() == null || arguments.name().isBlank()
                ? null
                : arguments.name().strip().toLowerCase(Locale.ROOT);

        List<Person> matching = personService.findByPatient(invocation.patient()).stream()
                .filter(p -> arguments.relationship() == null || p.getRelationship() == arguments.relationship())
                .filter(p -> needle == null || matches(p, needle))
                .toList();
        int limit = arguments.limit() != null ? arguments.limit() : MAX_LIMIT;
        return new Result(matching.stream().limit(limit).map(PersonMapper::toResponse).toList(), matching.size());
    }

    private static boolean matches(Person person, String needle) {
        return Stream.of(person.getFullName(), person.getCalledAs(), person.getRelationshipLabel())
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(needle));
    }
}
