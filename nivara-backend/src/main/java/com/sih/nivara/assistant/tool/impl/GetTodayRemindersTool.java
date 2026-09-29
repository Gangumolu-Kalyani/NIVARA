package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.NoArguments;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.mapper.ReminderMapper;
import com.sih.nivara.dto.response.ReminderOccurrenceResponse;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.ReminderSchedule;
import com.sih.nivara.service.ReminderService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Today's reminders and what has happened to each, in time order, for the patient's own day in
 * their timezone. Reuses {@link ReminderService#dailyCare}, which, like the dashboard, first makes
 * sure today's reminders exist.
 */
@Component
public class GetTodayRemindersTool implements AssistantTool<NoArguments> {

    static final int MAX_REMINDERS = 50;

    public record Result(LocalDate date, List<ReminderOccurrenceResponse> reminders, boolean truncated) {
    }

    private final ReminderService reminderService;

    public GetTodayRemindersTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "get_today_reminders";
    }

    @Override
    public String description() {
        return "Lists today's reminders (medicine, water, meals, movement, appointments, cognitive activities) "
                + "in time order, with whether each is still pending, was confirmed, missed or escalated.";
    }

    @Override
    public Class<NoArguments> argumentsType() {
        return NoArguments.class;
    }

    @Override
    public Map<String, Object> argumentProperties() {
        return Map.of();
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
    public Result execute(ToolInvocation<NoArguments> invocation) {
        LocalDate today = ReminderSchedule.localDate(invocation.patient(), invocation.now());
        List<ReminderOccurrenceResponse> reminders = reminderService.dailyCare(invocation.patient(), today).stream()
                .map(ReminderMapper::toOccurrenceResponse)
                .toList();
        return new Result(today, reminders.stream().limit(MAX_REMINDERS).toList(), reminders.size() > MAX_REMINDERS);
    }
}
