package com.sih.nivara.entity;

import com.sih.nivara.entity.enums.DayOfWeekCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps reminders.repeat_days, a comma-separated list such as 'MON,WED,FRI', onto a set of
 * {@link DayOfWeekCode}. Days are written in week order, so the same set always produces the same
 * column value. An empty set is stored as NULL, which is what ck_reminders_weekly_rule expects of
 * a reminder that is not WEEKLY.
 */
@Converter
public class DayOfWeekCodesConverter implements AttributeConverter<Set<DayOfWeekCode>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeekCode> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(days).stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeekCode> convertToEntityAttribute(String column) {
        Set<DayOfWeekCode> days = EnumSet.noneOf(DayOfWeekCode.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(","))
                    .map(String::trim)
                    .map(DayOfWeekCode::valueOf)
                    .forEach(days::add);
        }
        return days;
    }
}
