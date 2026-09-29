package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.enums.AssistantMode;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The system prompt for a conversation, built only from the {@link AssistantContext} the backend
 * resolved and authorized. It carries no identifiers; the patient's preferred name is used only when
 * the authorized context already holds one.
 */
public final class SystemPrompts {

    private static final DateTimeFormatter NOW =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private SystemPrompts() {
        // utility class
    }

    public static String forContext(AssistantContext context, Instant now) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(context.mode() == AssistantMode.PATIENT ? PATIENT : CAREGIVER);
        prompt.append(COMMON);

        ZonedDateTime local = now.atZone(context.timezone());
        prompt.append("\nThe current local date and time is ").append(NOW.format(local))
                .append(" (").append(context.timezone().getId()).append(").");
        prompt.append("\nAlways reply in ").append(languageName(context.languageCode()))
                .append(" (language code ").append(context.languageCode()).append("), even if tool results are in English.");

        String preferredName = context.patient() == null ? null : context.patient().getPreferredName();
        if (context.mode() == AssistantMode.PATIENT) {
            if (preferredName != null && !preferredName.isBlank()) {
                prompt.append("\nThe person you are talking with likes to be called ").append(preferredName.strip()).append('.');
            }
        } else if (context.patient() != null) {
            prompt.append("\nThis conversation is about one patient");
            if (preferredName != null && !preferredName.isBlank()) {
                prompt.append(", who likes to be called ").append(preferredName.strip());
            }
            prompt.append(". Tools already know which patient; do not pass a patientUuid.");
        } else {
            prompt.append("\nNo patient has been chosen for this conversation. Tools about a patient need one: "
                    + "if you do not have a patientUuid from an earlier tool result, ask the caregiver to "
                    + "open a conversation about that patient instead of guessing.");
        }
        return prompt.toString();
    }

    private static String languageName(String languageCode) {
        String name = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? languageCode : name;
    }

    private static final String PATIENT = """
            You are NIVARA, a gentle assistant for an older person who may have memory difficulties.
            Speak warmly and simply. Use short, simple sentences. Say one thing at a time.
            Be patient and reassuring. Never make the person feel they should have remembered something.
            """;

    private static final String CAREGIVER = """
            You are NIVARA, an assistant for a caregiver looking after an older person.
            Be concise and factual. Lead with the answer, then only the details that matter.
            """;

    private static final String COMMON = """
            Rules:
            - For anything about the person's reminders, people, memories, alerts or day, use the tools. \
            Never invent or guess reminders, people, memories or alerts. If a tool returns nothing, say so plainly.
            - If a tool returns an error, do not retry it with guessed values; explain simply what you could not do.
            - Do not give medical advice: no diagnoses, no medicine doses or changes, no treatment suggestions. \
            For health concerns, suggest speaking with the care team or a doctor.
            - Never mention internal identifiers such as uuids, ids or codes to the person you are talking with.
            - Keep replies short enough to be read aloud.
            """;
}
