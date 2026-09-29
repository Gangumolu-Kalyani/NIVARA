package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.AlertCategory;
import com.sih.nivara.entity.enums.AlertSeverity;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.entity.enums.AlertType;

import java.time.Instant;
import java.util.UUID;

/**
 * An alert as the API returns it. resolvedAt and resolvedByName are set once the alert is closed,
 * whether RESOLVED or DISMISSED. Only the closing caregiver's name is shown, never their email.
 */
public record AlertResponse(
        UUID uuid,
        UUID patientUuid,
        UUID reminderOccurrenceUuid,
        AlertType alertType,
        AlertCategory category,
        AlertSeverity severity,
        String title,
        String message,
        AlertStatus status,
        Instant createdAt,
        Instant resolvedAt,
        String resolvedByName) {
}
