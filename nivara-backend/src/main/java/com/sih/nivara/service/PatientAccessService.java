package com.sih.nivara.service;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PatientCaregiver;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The one place that decides whether the authenticated account may touch a patient.
 *
 * <p>The acting account comes only from {@link CurrentUserProvider}, that is from the access
 * token; no request can name it. Access is decided solely by the account's row in
 * patient_caregivers, never by patients.created_by_user_id and never by the account's role, so an
 * ADMIN gets no bypass. Levels are ranked OWNER > EDITOR > VIEWER, and a higher level allows
 * everything a lower one does.
 *
 * <p>An account with no link to a patient is told the patient does not exist (404), exactly as if
 * the uuid were unknown, so uuids cannot be probed for patients that belong to someone else. An
 * account that does have a link but too low a level is told so (403): it already knows the
 * patient exists, so nothing leaks.
 *
 * <p>The check runs again on every request, so granting, changing or revoking access takes effect
 * immediately, including for tokens issued earlier.
 */
@Service
@Transactional(readOnly = true)
public class PatientAccessService {

    /** A patient together with the acting account's access level to it. */
    public record PatientAccess(Patient patient, AccessLevel accessLevel) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final PatientService patientService;
    private final PatientCaregiverService patientCaregiverService;

    public PatientAccessService(CurrentUserProvider currentUserProvider,
                                PatientService patientService,
                                PatientCaregiverService patientCaregiverService) {
        this.currentUserProvider = currentUserProvider;
        this.patientService = patientService;
        this.patientCaregiverService = patientCaregiverService;
    }

    /** The authenticated account; 401 if the request is not authenticated. */
    public AppUser requireCaller() {
        return currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    /**
     * Resolves a patient from a request path and checks the caller's access to it. 404 when the
     * patient does not exist or the caller has no link to it; 403 when the link is below minimum.
     */
    public PatientAccess requirePatientAccess(UUID patientUuid, AccessLevel minimum) {
        AppUser caller = requireCaller();
        Supplier<ResponseStatusException> notFound = () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No patient with uuid " + patientUuid);
        Patient patient = patientService.findByUuid(patientUuid).orElseThrow(notFound);
        return new PatientAccess(patient, checkLevel(patient, caller, minimum, notFound));
    }

    /** {@link #requirePatientAccess} when only the patient is needed. */
    public Patient requirePatient(UUID patientUuid, AccessLevel minimum) {
        return requirePatientAccess(patientUuid, minimum).patient();
    }

    /**
     * Checks the caller's access to the patient an already-loaded item belongs to. The caller
     * supplies the item's own 404, so an item under someone else's patient looks exactly like an
     * item that does not exist.
     */
    public void requireAccess(Patient patient, AccessLevel minimum, Supplier<ResponseStatusException> notFound) {
        checkLevel(patient, requireCaller(), minimum, notFound);
    }

    /** Every patient the caller can reach, with the caller's level to each, by patient name. */
    public List<PatientAccess> accessiblePatients() {
        return patientCaregiverService.findByCaregiver(requireCaller()).stream()
                .map(link -> new PatientAccess(link.getPatient(), link.getAccessLevel()))
                .toList();
    }

    /** Whether a granted level is at least the required one: OWNER > EDITOR > VIEWER. */
    public static boolean allows(AccessLevel granted, AccessLevel required) {
        return rank(granted) >= rank(required);
    }

    private AccessLevel checkLevel(Patient patient, AppUser caller, AccessLevel minimum,
                                   Supplier<ResponseStatusException> notFound) {
        AccessLevel granted = patientCaregiverService.findLink(patient, caller)
                .map(PatientCaregiver::getAccessLevel)
                .orElseThrow(notFound);
        if (!allows(granted, minimum)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    minimum + " access to this patient is required; this account has " + granted);
        }
        return granted;
    }

    /** Explicit ranks, so the order never depends on how the enum constants happen to be declared. */
    private static int rank(AccessLevel level) {
        return switch (level) {
            case VIEWER -> 1;
            case EDITOR -> 2;
            case OWNER -> 3;
        };
    }
}
