package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.JsonSchema;
import com.sih.nivara.assistant.tool.ToolErrorCode;
import com.sih.nivara.assistant.tool.ToolException;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.mapper.MemoryMapper;
import com.sih.nivara.dto.response.MemoryResponse;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.MemoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The patient's memories, most recent first, from {@link MemoryService#findByPatient}, narrowed
 * by text, date range and the people involved. Answers "Who visited me yesterday?".
 */
@Component
public class SearchMemoriesTool implements AssistantTool<SearchMemoriesTool.Arguments> {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;

    public record Arguments(
            @Size(max = 200) String query,
            @Size(max = 120) String personName,
            LocalDate fromDate,
            LocalDate toDate,
            @Min(1) @Max(MAX_LIMIT) Integer limit) {
    }

    /** totalMatching counts every matching memory, not only the ones returned. */
    public record Result(List<MemoryResponse> memories, int totalMatching) {
    }

    private final MemoryService memoryService;

    public SearchMemoriesTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "search_memories";
    }

    @Override
    public String description() {
        return "Searches the patient's memories (visits, outings, meals, celebrations, conversations and other "
                + "events), most recent first. Narrow by words in the title or description, by a person involved, "
                + "and by a date range.";
    }

    @Override
    public Class<Arguments> argumentsType() {
        return Arguments.class;
    }

    @Override
    public Map<String, Object> argumentProperties() {
        return JsonSchema.ordered(
                "query", JsonSchema.string("Words to look for in the memory's title or description; any case.", 200),
                "personName", JsonSchema.string("Part of the name of a person involved in the memory.", 120),
                "fromDate", JsonSchema.date("Only memories on or after this day, YYYY-MM-DD."),
                "toDate", JsonSchema.date("Only memories on or before this day, YYYY-MM-DD."),
                "limit", JsonSchema.integer("How many memories to return, most recent first. Default "
                        + DEFAULT_LIMIT + ".", 1, MAX_LIMIT));
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
    public Result execute(ToolInvocation<Arguments> invocation) throws ToolException {
        Arguments arguments = invocation.arguments();
        if (arguments.fromDate() != null && arguments.toDate() != null && arguments.fromDate().isAfter(arguments.toDate())) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, "fromDate: must not be after toDate");
        }
        String query = lowerOrNull(arguments.query());
        String person = lowerOrNull(arguments.personName());

        List<Memory> matching = memoryService.findByPatient(invocation.patient()).stream()
                .filter(m -> arguments.fromDate() == null || !m.getOccurredOn().isBefore(arguments.fromDate()))
                .filter(m -> arguments.toDate() == null || !m.getOccurredOn().isAfter(arguments.toDate()))
                .filter(m -> query == null || contains(m.getTitle(), query) || contains(m.getDescription(), query))
                .filter(m -> person == null || m.getPeople().stream()
                        .anyMatch(p -> contains(p.getFullName(), person) || contains(p.getCalledAs(), person)))
                .toList();
        int limit = arguments.limit() != null ? arguments.limit() : DEFAULT_LIMIT;
        return new Result(matching.stream().limit(limit).map(MemoryMapper::toResponse).toList(), matching.size());
    }

    private static String lowerOrNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
