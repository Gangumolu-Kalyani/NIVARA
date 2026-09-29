package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.AlertMapper;
import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.response.ActivityCards;
import com.sih.nivara.dto.response.ActivityCards.ActivityCard;
import com.sih.nivara.dto.response.DailySummaryResponse;
import com.sih.nivara.dto.response.DailySummaryResponse.CareRhythm;
import com.sih.nivara.dto.response.DailySummaryResponse.CareRhythmLevel;
import com.sih.nivara.dto.response.DashboardSummaryResponse;
import com.sih.nivara.dto.response.DashboardSummaryResponse.CareStatus;
import com.sih.nivara.dto.response.DashboardSummaryResponse.PatientRef;
import com.sih.nivara.dto.response.DashboardSummaryResponse.ReminderSummary;
import com.sih.nivara.dto.response.ProgressResponse;
import com.sih.nivara.dto.response.ProgressResponse.DailyAccuracyPoint;
import com.sih.nivara.dto.response.ProgressResponse.PerformanceTrend;
import com.sih.nivara.entity.Alert;
import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.AlertSeverity;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameResultStatus;
import com.sih.nivara.entity.enums.PatientResponseType;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.entity.enums.ReminderResponseStatus;
import com.sih.nivara.repository.GameResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Read models for the caregiver dashboard: today's summary, cognitive progress and the daily
 * summary. Everything is computed on request from reminder occurrences, alerts and game results,
 * so nothing here can drift out of date. Days are the patient's calendar days, in their timezone.
 *
 * <p>The methods that cover today first make sure today's occurrences exist, which is why they are
 * not read-only.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** How far ahead "upcoming appointments" looks. */
    static final Duration APPOINTMENT_HORIZON = Duration.ofDays(7);

    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final LocalTime AFTERNOON_STARTS = LocalTime.NOON;
    private static final LocalTime EVENING_STARTS = LocalTime.of(17, 0);

    private final ReminderService reminderService;
    private final AlertService alertService;
    private final GameResultRepository gameResultRepository;

    public DashboardService(ReminderService reminderService,
                            AlertService alertService,
                            GameResultRepository gameResultRepository) {
        this.reminderService = reminderService;
        this.alertService = alertService;
        this.gameResultRepository = gameResultRepository;
    }

    // ---- dashboard ---------------------------------------------------------------------------

    @Transactional
    public DashboardSummaryResponse summary(Patient patient) {
        Instant now = Instant.now();
        LocalDate today = ReminderSchedule.localDate(patient, now);
        List<ReminderOccurrence> occurrences = reminderService.dailyCare(patient, today);
        List<Alert> openAlerts = alertService.findByPatient(patient, AlertStatus.OPEN);

        int completed = count(occurrences, o -> o.getResponseStatus() == ReminderResponseStatus.COMPLETED);
        int pending = count(occurrences, ReminderOccurrence::isAwaitingResponse);
        int missed = count(occurrences, DashboardService::isMissedOrEscalated);

        boolean needsAttention = missed > 0
                || openAlerts.stream().anyMatch(a -> a.getSeverity() == AlertSeverity.HIGH);

        return new DashboardSummaryResponse(
                new PatientRef(patient.getUuid(), patient.getFullName()),
                needsAttention ? CareStatus.NEEDS_ATTENTION : CareStatus.ON_TRACK,
                activityCards(occurrences),
                new ReminderSummary(occurrences.size(), completed, pending, missed,
                        upcomingAppointments(patient, now)),
                openAlerts.stream().map(AlertMapper::toResponse).toList(),
                now);
    }

    // ---- progress ----------------------------------------------------------------------------

    /** Game performance over the last rangeDays days, today included. */
    public ProgressResponse progress(Patient patient, int rangeDays) {
        ZoneId zone = ReminderSchedule.zoneOf(patient);
        LocalDate today = ReminderSchedule.localDate(patient, Instant.now());
        LocalDate firstDay = today.minusDays(rangeDays - 1L);

        List<GameResult> results = gameResultRepository
                .findByPatientAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDescIdDesc(
                        patient,
                        firstDay.atStartOfDay(zone).toInstant(),
                        today.plusDays(1).atStartOfDay(zone).toInstant());
        List<GameResult> completed = results.stream().filter(DashboardService::isCompleted).toList();

        Map<String, Integer> difficulty = new TreeMap<>();
        results.forEach(r -> difficulty.merge(String.valueOf(r.getDifficultyLevel()), 1, Integer::sum));

        Map<CognitiveDomain, Double> domainAccuracy = new EnumMap<>(CognitiveDomain.class);
        completed.stream()
                .filter(r -> r.getAccuracy() != null)
                .collect(Collectors.groupingBy(GameResult::getCognitiveDomain))
                .forEach((domain, domainResults) -> domainAccuracy.put(domain, averageAccuracy(domainResults)));

        Map<LocalDate, List<GameResult>> byDay = results.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.getStartedAt(), zone)));
        List<DailyAccuracyPoint> trend = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
            List<GameResult> dayResults = byDay.getOrDefault(day, List.of());
            trend.add(new DailyAccuracyPoint(day,
                    averageAccuracy(dayResults.stream().filter(DashboardService::isCompleted).toList()),
                    dayResults.size()));
        }

        LocalDate secondHalfStarts = firstDay.plusDays(rangeDays / 2);
        OptionalDouble avgReaction = completed.stream()
                .filter(r -> r.getAvgReactionTimeMs() != null)
                .mapToInt(GameResult::getAvgReactionTimeMs)
                .average();

        return new ProgressResponse(
                rangeDays,
                completed.size(),
                count(results, r -> r.getStatus() == GameResultStatus.ABANDONED),
                averageAccuracy(completed),
                avgReaction.isPresent() ? (int) Math.round(avgReaction.getAsDouble()) : null,
                difficulty,
                domainAccuracy,
                trendOf(completed, zone, secondHalfStarts),
                trend,
                results.stream().limit(RECENT_ACTIVITY_LIMIT).map(GameResultMapper::toSummaryResponse).toList());
    }

    // ---- daily summary -----------------------------------------------------------------------

    @Transactional
    public DailySummaryResponse dailySummary(Patient patient, LocalDate date) {
        Instant now = Instant.now();
        ZoneId zone = ReminderSchedule.zoneOf(patient);
        List<ReminderOccurrence> occurrences = reminderService.dailyCare(patient, date);
        List<ReminderOccurrence> due = occurrences.stream().filter(o -> !o.getScheduledAt().isAfter(now)).toList();
        List<GameResult> games = gameResultRepository
                .findByPatientAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDescIdDesc(
                        patient, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());

        Instant from = date.atStartOfDay(zone).toInstant();
        return new DailySummaryResponse(
                date,
                activityCards(occurrences),
                upcomingAppointments(patient, from.isAfter(now) ? from : now),
                summaryLines(occurrences, games),
                new CareRhythm(rhythm(due, patient, Slot.MORNING), rhythm(due, patient, Slot.AFTERNOON),
                        rhythm(due, patient, Slot.EVENING)),
                patterns(patient, date, now));
    }

    private List<String> summaryLines(List<ReminderOccurrence> occurrences, List<GameResult> games) {
        List<String> lines = new ArrayList<>();
        if (occurrences.isEmpty()) {
            lines.add("No reminders were scheduled for this day.");
        }

        Map<ReminderCategory, List<ReminderOccurrence>> byCategory = new EnumMap<>(ReminderCategory.class);
        occurrences.forEach(o -> byCategory.computeIfAbsent(o.getReminder().getCategory(), c -> new ArrayList<>()).add(o));
        byCategory.forEach((category, list) -> lines.add(label(category) + ": "
                + count(list, o -> o.getResponseStatus() == ReminderResponseStatus.COMPLETED)
                + " of " + list.size() + " confirmed."));

        int escalated = count(occurrences, ReminderOccurrence::isEscalated);
        if (escalated > 0) {
            lines.add(escalated + (escalated == 1 ? " reminder" : " reminders") + " needed the care team's attention.");
        }
        int helpRequests = count(occurrences, o -> o.getResponseType() == PatientResponseType.NEED_HELP);
        if (helpRequests > 0) {
            lines.add("Asked for help " + times(helpRequests) + ".");
        }

        List<GameResult> completedGames = games.stream().filter(DashboardService::isCompleted).toList();
        if (completedGames.isEmpty()) {
            lines.add("No cognitive games were completed.");
        } else {
            Double accuracy = averageAccuracy(completedGames);
            lines.add("Completed " + completedGames.size() + (completedGames.size() == 1 ? " cognitive game" : " cognitive games")
                    + (accuracy == null ? "." : ", with " + Math.round(accuracy) + "% average accuracy."));
        }
        return lines;
    }

    /** Observations over the seven days ending on this date, from reminders that had fallen due. */
    private List<String> patterns(Patient patient, LocalDate date, Instant now) {
        List<ReminderOccurrence> week = reminderService.occurrencesBetween(patient, date.minusDays(6), date).stream()
                .filter(o -> !o.getScheduledAt().isAfter(now))
                .toList();
        List<String> patterns = new ArrayList<>();

        Map<String, List<ReminderOccurrence>> bySlotAndCategory = new LinkedHashMap<>();
        for (Slot slot : Slot.values()) {
            for (ReminderCategory category : ReminderCategory.values()) {
                List<ReminderOccurrence> group = week.stream()
                        .filter(o -> o.getReminder().getCategory() == category && slotOf(patient, o) == slot)
                        .toList();
                if (!group.isEmpty()) {
                    bySlotAndCategory.put(slot.label + " " + label(category).toLowerCase(), group);
                }
            }
        }
        bySlotAndCategory.forEach((name, group) -> {
            int missed = count(group, DashboardService::isMissedOrEscalated);
            if (group.size() >= 3 && missed * 2 >= group.size()) {
                patterns.add(capitalize(name) + ": missed " + missed + " of " + group.size() + " times this week.");
            }
        });

        for (Slot slot : Slot.values()) {
            List<ReminderOccurrence> completed = week.stream()
                    .filter(o -> slotOf(patient, o) == slot && o.getResponseStatus() == ReminderResponseStatus.COMPLETED)
                    .toList();
            double avgNudges = completed.stream().mapToInt(ReminderOccurrence::getNudgeCount).average().orElse(0);
            if (completed.size() >= 3 && avgNudges >= 2) {
                patterns.add(capitalize(slot.label) + " reminders usually need more than one prompt.");
            }
        }

        int helpRequests = count(week, o -> o.getResponseType() == PatientResponseType.NEED_HELP);
        if (helpRequests >= 2) {
            patterns.add("Asked for help " + times(helpRequests) + " this week.");
        }
        return patterns;
    }

    private CareRhythmLevel rhythm(List<ReminderOccurrence> due, Patient patient, Slot slot) {
        List<ReminderOccurrence> inSlot = due.stream().filter(o -> slotOf(patient, o) == slot).toList();
        if (inSlot.isEmpty()) {
            return CareRhythmLevel.NO_DATA;
        }
        List<ReminderOccurrence> completed = inSlot.stream()
                .filter(o -> o.getResponseStatus() == ReminderResponseStatus.COMPLETED)
                .toList();
        double ratio = (double) completed.size() / inSlot.size();
        double avgNudges = completed.stream().mapToInt(ReminderOccurrence::getNudgeCount).average().orElse(0);
        if (ratio >= 0.8 && avgNudges <= 1.5) {
            return CareRhythmLevel.STRONG;
        }
        return ratio >= 0.5 ? CareRhythmLevel.NEEDS_PROMPTING : CareRhythmLevel.LOW;
    }

    // ---- shared ------------------------------------------------------------------------------

    private static ActivityCards activityCards(List<ReminderOccurrence> occurrences) {
        return new ActivityCards(
                card(occurrences, ReminderCategory.COGNITIVE_ACTIVITY),
                card(occurrences, ReminderCategory.MEDICINE),
                card(occurrences, ReminderCategory.HYDRATION),
                card(occurrences, ReminderCategory.MOVEMENT));
    }

    private static ActivityCard card(List<ReminderOccurrence> occurrences, ReminderCategory category) {
        List<ReminderOccurrence> inCategory = occurrences.stream()
                .filter(o -> o.getReminder().getCategory() == category)
                .toList();
        return new ActivityCard(
                count(inCategory, o -> o.getResponseStatus() == ReminderResponseStatus.COMPLETED),
                inCategory.size());
    }

    /** APPOINTMENT reminders falling due in [from, from + horizon). */
    private int upcomingAppointments(Patient patient, Instant from) {
        Instant until = from.plus(APPOINTMENT_HORIZON);
        LocalDate firstDay = ReminderSchedule.localDate(patient, from);
        LocalDate lastDay = ReminderSchedule.localDate(patient, until);
        int count = 0;
        for (Reminder reminder : reminderService.findByPatient(patient)) {
            if (reminder.getCategory() != ReminderCategory.APPOINTMENT) {
                continue;
            }
            for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
                Instant at = ReminderSchedule.scheduledAt(reminder, day);
                if (ReminderSchedule.isDueOn(reminder, day) && !at.isBefore(from) && at.isBefore(until)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Average accuracy of these attempts, rounded to one decimal; null when none has one. */
    private static Double averageAccuracy(List<GameResult> results) {
        OptionalDouble average = results.stream()
                .map(GameResult::getAccuracy)
                .filter(a -> a != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        return average.isPresent() ? Math.round(average.getAsDouble() * 10) / 10.0 : null;
    }

    private static PerformanceTrend trendOf(List<GameResult> completed, ZoneId zone, LocalDate secondHalfStarts) {
        List<GameResult> withAccuracy = completed.stream().filter(r -> r.getAccuracy() != null).toList();
        Predicate<GameResult> inSecondHalf = r -> !LocalDate.ofInstant(r.getStartedAt(), zone).isBefore(secondHalfStarts);
        List<GameResult> first = withAccuracy.stream().filter(inSecondHalf.negate()).toList();
        List<GameResult> second = withAccuracy.stream().filter(inSecondHalf).toList();
        if (first.size() < 2 || second.size() < 2) {
            return PerformanceTrend.INSUFFICIENT_DATA;
        }
        double change = averageAccuracy(second) - averageAccuracy(first);
        if (change > 5) {
            return PerformanceTrend.UP;
        }
        return change < -5 ? PerformanceTrend.DOWN : PerformanceTrend.STABLE;
    }

    private static boolean isCompleted(GameResult result) {
        return result.getStatus() == GameResultStatus.COMPLETED;
    }

    private static boolean isMissedOrEscalated(ReminderOccurrence occurrence) {
        return occurrence.getResponseStatus() == ReminderResponseStatus.MISSED
                || occurrence.getResponseStatus() == ReminderResponseStatus.ESCALATED;
    }

    private static <T> int count(List<T> items, Predicate<T> predicate) {
        return (int) items.stream().filter(predicate).count();
    }

    private enum Slot {
        MORNING("morning"),
        AFTERNOON("afternoon"),
        EVENING("evening");

        private final String label;

        Slot(String label) {
            this.label = label;
        }
    }

    private static Slot slotOf(Patient patient, ReminderOccurrence occurrence) {
        LocalTime time = ReminderSchedule.localTime(patient, occurrence.getScheduledAt());
        if (time.isBefore(AFTERNOON_STARTS)) {
            return Slot.MORNING;
        }
        return time.isBefore(EVENING_STARTS) ? Slot.AFTERNOON : Slot.EVENING;
    }

    private static String label(ReminderCategory category) {
        return switch (category) {
            case MEDICINE -> "Medicine";
            case HYDRATION -> "Water";
            case APPOINTMENT -> "Appointments";
            case MOVEMENT -> "Movement";
            case COGNITIVE_ACTIVITY -> "Cognitive activities";
            case MEAL -> "Meals";
        };
    }

    private static String times(int n) {
        return n == 1 ? "once" : n == 2 ? "twice" : n + " times";
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
