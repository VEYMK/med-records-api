package com.example.medrecords.domain;

/**
 * Appointment lifecycle.
 *
 * <pre>
 * SCHEDULED --(doctor)--> COMPLETED
 * SCHEDULED --(doctor)--> CANCELLED
 * SCHEDULED --(patient)-> CANCELLED
 * </pre>
 */
public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
