package com.sih.nivara.entity.enums;

/**
 * patient_caregivers.relationship - CHECK ck_patient_caregivers_relationship (V1)
 * and people.relationship - CHECK ck_people_relationship (V2).
 * The two CHECK lists are identical, so one enum serves both columns.
 */
public enum RelationshipType {
    SPOUSE,
    SON,
    DAUGHTER,
    SON_IN_LAW,
    DAUGHTER_IN_LAW,
    GRANDCHILD,
    SIBLING,
    RELATIVE,
    FRIEND,
    NEIGHBOUR,
    CAREGIVER,
    DOCTOR,
    OTHER
}
