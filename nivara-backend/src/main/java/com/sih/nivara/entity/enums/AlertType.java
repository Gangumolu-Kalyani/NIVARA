package com.sih.nivara.entity.enums;

/** alerts.alert_type - CHECK ck_alerts_type (V5, widened in V6). */
public enum AlertType {
    REMINDER_ESCALATION,
    /** The patient asked for help outside any reminder (V6). */
    HELP_REQUEST,
    OTHER
}
