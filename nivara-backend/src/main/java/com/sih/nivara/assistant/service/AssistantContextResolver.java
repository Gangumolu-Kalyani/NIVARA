package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.UserRole;
import com.sih.nivara.security.CurrentPatientProvider;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.ReminderSchedule;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Works out who the assistant is talking to and about whom, from the authenticated account only.
 *
 * <ul>
 *   <li>A PATIENT account talks as the patient, about the patient linked to it through
 *       {@link CurrentPatientProvider}. Nothing in a request can point it at another patient.</li>
 *   <li>A CAREGIVER or ADMIN account talks as a caregiver, optionally about one patient. Whether it
 *       may is decided by {@link PatientAccessService} alone, when the conversation starts and
 *       again on every later request, so revoked access takes effect at once.</li>
 * </ul>
 *
 * <p>For an existing conversation, any failed check answers 404, exactly like a conversation that
 * does not exist, so conversations cannot be probed.
 */
@Component
public class AssistantContextResolver {

    /** The minimum care-team level for talking about a patient. Tools may require more. */
    static final AccessLevel CONVERSATION_ACCESS = AccessLevel.VIEWER;

    /** Used when there is no patient whose timezone applies. */
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final PatientAccessService patientAccessService;
    private final CurrentPatientProvider currentPatientProvider;

    public AssistantContextResolver(PatientAccessService patientAccessService,
                                    CurrentPatientProvider currentPatientProvider) {
        this.patientAccessService = patientAccessService;
        this.currentPatientProvider = currentPatientProvider;
    }

    /** The authenticated account; 401 when there is none. */
    public AppUser requireCaller() {
        return patientAccessService.requireCaller();
    }

    /**
     * The context for a new conversation.
     *
     * @param requestedPatientUuid a caregiver's choice of patient, or null. A patient may only
     *                             name themselves; any other uuid is refused (403).
     * @param requestedLanguage    a language to use instead of the default, or null
     */
    public AssistantContext forNewConversation(AppUser caller, UUID requestedPatientUuid, String requestedLanguage) {
        if (caller.getRole() == UserRole.PATIENT) {
            Patient own = currentPatientProvider.requirePatient();
            if (requestedPatientUuid != null && !requestedPatientUuid.equals(own.getUuid())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A patient's assistant can only talk about that patient");
            }
            return patientContext(caller, own,
                    requestedLanguage != null ? requestedLanguage : own.getPreferredLanguage());
        }

        Patient patient = requestedPatientUuid == null
                ? null
                : patientAccessService.requirePatient(requestedPatientUuid, CONVERSATION_ACCESS);
        return caregiverContext(caller, patient,
                requestedLanguage != null ? requestedLanguage : caller.getPreferredLanguage());
    }

    /**
     * The context for an existing conversation, which the caller already owns. Re-checks that the
     * caller may still talk about its patient; 404 otherwise.
     */
    public AssistantContext forConversation(AppUser caller, AssistantConversation conversation) {
        Supplier<ResponseStatusException> notFound = () -> notFound(conversation.getUuid());

        if (conversation.getMode() == AssistantMode.PATIENT) {
            if (caller.getRole() != UserRole.PATIENT) {
                throw notFound.get();
            }
            Patient own = currentPatientProvider.currentPatient().orElseThrow(notFound);
            if (!own.getId().equals(conversation.getPatient().getId())) {
                throw notFound.get();
            }
            return patientContext(caller, own, conversation.getLanguageCode());
        }

        if (caller.getRole() == UserRole.PATIENT) {
            throw notFound.get();
        }
        Patient patient = conversation.getPatient();
        if (patient != null) {
            patientAccessService.requireAccess(patient, CONVERSATION_ACCESS, notFound);
        }
        return caregiverContext(caller, patient, conversation.getLanguageCode());
    }

    public static ResponseStatusException notFound(UUID conversationUuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No conversation with uuid " + conversationUuid);
    }

    private static AssistantContext patientContext(AppUser caller, Patient patient, String language) {
        return new AssistantContext(caller, AssistantMode.PATIENT, patient, language, ReminderSchedule.zoneOf(patient));
    }

    private static AssistantContext caregiverContext(AppUser caller, Patient patient, String language) {
        return new AssistantContext(caller, AssistantMode.CAREGIVER, patient, language,
                patient != null ? ReminderSchedule.zoneOf(patient) : DEFAULT_ZONE);
    }
}
