package com.example.medrecords.domain;

/**
 * Application roles. The domain is intentionally small: a doctor and a patient.
 * The role is stored as a string in the database and is embedded into the JWT.
 */
public enum Role {
    DOCTOR,
    PATIENT
}
