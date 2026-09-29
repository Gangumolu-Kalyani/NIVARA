package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.JsonSchema;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.mapper.AlertMapper;
import com.sih.nivara.dto.response.AlertResponse;
import com.sih.nivara.entity.Alert;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.service.AlertService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The patient's alerts, newest first, from {@link AlertService#findByPatient}. For caregivers
 * only: alerts are messages for the care team.
 */
@Component
public class GetAlertsTool implements AssistantTool<GetAlertsTool.Arguments> {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;

    /** status null means every status. */
    public record Arguments(
            AlertStatus status,
            @Min(1) @Max(MAX_LIMIT) Integer limit) {
    }

    /** totalMatching counts every matching alert, not only the ones returned. */
    public record Result(List<AlertResponse> alerts, int totalMatching) {
    }

    private final AlertService alertService;

    public GetAlertsTool(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String name() {
        return "get_alerts";
    }

    @Override
    public String description() {
        return "Lists the patient's alerts for the care team, newest first: missed or unanswered reminders "
                + "and requests for help. Filter by status; OPEN alerts still need attention.";
    }

    @Override
    public Class<Arguments> argumentsType() {
        return Arguments.class;
    }

    @Override
    public Map<String, Object> argumentProperties() {
        return JsonSchema.ordered(
                "status", JsonSchema.oneOf("Only alerts with this status. Omit for every status.", AlertStatus.values()),
                "limit", JsonSchema.integer("How many alerts to return, newest first. Default " + DEFAULT_LIMIT + ".",
                        1, MAX_LIMIT));
    }

    @Override
    public Set<AssistantMode> modes() {
        return Set.of(AssistantMode.CAREGIVER);
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
        List<Alert> alerts = alertService.findByPatient(invocation.patient(), arguments.status());
        int limit = arguments.limit() != null ? arguments.limit() : DEFAULT_LIMIT;
        return new Result(alerts.stream().limit(limit).map(AlertMapper::toResponse).toList(), alerts.size());
    }
}
