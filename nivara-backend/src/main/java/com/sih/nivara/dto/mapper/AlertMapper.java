package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.response.AlertResponse;
import com.sih.nivara.entity.Alert;

/**
 * Converts {@link Alert} to its response. Reads the patient, reminder occurrence and resolving
 * account, which are lazy, so the alert must have been loaded with them fetched.
 */
public final class AlertMapper {

    private AlertMapper() {
        // utility class
    }

    public static AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getUuid(),
                alert.getPatient().getUuid(),
                alert.getReminderOccurrence() == null ? null : alert.getReminderOccurrence().getUuid(),
                alert.getAlertType(),
                alert.getCategory(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getResolvedByUser() == null ? null : alert.getResolvedByUser().getFullName());
    }
}
