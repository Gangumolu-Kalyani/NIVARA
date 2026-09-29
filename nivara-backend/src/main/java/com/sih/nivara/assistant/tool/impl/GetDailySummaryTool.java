package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.JsonSchema;
import com.sih.nivara.assistant.tool.ToolErrorCode;
import com.sih.nivara.assistant.tool.ToolException;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.response.DailySummaryResponse;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.DashboardService;
import com.sih.nivara.service.ReminderSchedule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * A plain-language summary of one of the patient's days, from
 * {@link DashboardService#dailySummary}. For caregivers only.
 */
@Component
public class GetDailySummaryTool implements AssistantTool<GetDailySummaryTool.Arguments> {

    /** date null means today, in the patient's timezone. */
    public record Arguments(LocalDate date) {
    }

    private final DashboardService dashboardService;

    public GetDailySummaryTool(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    public String name() {
        return "get_daily_summary";
    }

    @Override
    public String description() {
        return "Summarizes how one of the patient's days went: reminders confirmed and missed, games played, "
                + "care rhythm through the day, and patterns over the past week.";
    }

    @Override
    public Class<Arguments> argumentsType() {
        return Arguments.class;
    }

    @Override
    public Map<String, Object> argumentProperties() {
        return JsonSchema.ordered(
                "date", JsonSchema.date("The day to summarize, YYYY-MM-DD. Omit for today. Cannot be in the future."));
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
    public DailySummaryResponse execute(ToolInvocation<Arguments> invocation) throws ToolException {
        LocalDate today = ReminderSchedule.localDate(invocation.patient(), invocation.now());
        LocalDate date = invocation.arguments().date() != null ? invocation.arguments().date() : today;
        if (date.isAfter(today)) {
            throw new ToolException(ToolErrorCode.INVALID_ARGUMENTS, "date: cannot be in the future");
        }
        return dashboardService.dailySummary(invocation.patient(), date);
    }
}
