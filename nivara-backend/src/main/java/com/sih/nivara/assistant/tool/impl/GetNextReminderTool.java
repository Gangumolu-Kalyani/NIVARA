package com.sih.nivara.assistant.tool.impl;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.NoArguments;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.dto.mapper.ReminderMapper;
import com.sih.nivara.dto.response.ReminderOccurrenceResponse;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.ReminderSchedule;
import com.sih.nivara.service.ReminderService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The next reminder today: the earliest of today's reminders still waiting for an answer and due
 * at or after now. dueNow lists today's reminders that are already due and still unanswered.
 * Looks at today only, because reminders exist only for the current day.
 */
@Component
public class GetNextReminderTool implements AssistantTool<NoArguments> {

    /**
     * next is null when nothing else is scheduled today; dueNow is oldest first.
     */
    public record Result(LocalDate date, ReminderOccurrenceResponse next, List<ReminderOccurrenceResponse> dueNow) {
    }

    private final ReminderService reminderService;

    public GetNextReminderTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "get_next_reminder";
    }

    @Override
    public String description() {
        return "Finds the next reminder still to come today, and any of today's reminders that are already due "
                + "and not yet answered. Returns no next reminder when nothing else is scheduled today.";
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
        List<ReminderOccurrence> awaiting = reminderService.dailyCare(invocation.patient(), today).stream()
                .filter(ReminderOccurrence::isAwaitingResponse)
                .toList();

        ReminderOccurrenceResponse next = awaiting.stream()
                .filter(o -> !o.getScheduledAt().isBefore(invocation.now()))
                .findFirst()
                .map(ReminderMapper::toOccurrenceResponse)
                .orElse(null);
        List<ReminderOccurrenceResponse> dueNow = awaiting.stream()
                .filter(o -> o.getScheduledAt().isBefore(invocation.now()))
                .map(ReminderMapper::toOccurrenceResponse)
                .toList();
        return new Result(today, next, dueNow);
    }
}
