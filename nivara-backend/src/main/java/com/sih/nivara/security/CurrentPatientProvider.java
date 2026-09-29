package com.sih.nivara.security;

import com.sih.nivara.entity.Patient;

import java.util.Optional;

/**
 * Which patient is acting on this request, when the caller is a patient signed in on a paired
 * device. The single seam through which patient-facing code asks "whose data is this", in the way
 * {@link CurrentUserProvider} answers "who is calling".
 *
 * <p>The patient is always derived from the authenticated account's own link
 * (patients.user_account_id), never from anything in the request, so a patient can only ever
 * reach their own record.
 */
public interface CurrentPatientProvider {

    /** The caller's own patient record, or empty when the caller is not a patient. */
    Optional<Patient> currentPatient();

    /** The caller's own patient record; 403 when the caller is not a patient. */
    Patient requirePatient();
}
