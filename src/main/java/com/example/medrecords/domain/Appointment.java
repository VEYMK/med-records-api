package com.example.medrecords.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A booked medical appointment. This is the second table in the schema and it
 * has two foreign keys to {@link User} (doctor and patient), which demonstrates
 * a real relational model instead of a toy single-table CRUD.
 *
 * <p>Index design:</p>
 * <ul>
 *   <li>{@code idx_appointments_appointment_date} - global date index. Used by
 *       reporting / "appointments for a period" queries.</li>
 *   <li>{@code idx_appointments_doctor_date} - composite index for the most
 *       common access path: "doctor's schedule ordered by date". The leftmost
 *       column is the FK, the second column is the sort key, so PostgreSQL can
 *       use it for both filtering and ordering.</li>
 *   <li>{@code idx_appointments_patient_date} - same idea for a patient's
 *       history.</li>
 *   <li>{@code uk_appointments_doctor_slot} - a unique constraint that makes
 *       double booking of the same doctor + timestamp impossible even under a
 *       race (the database, not the application, is the source of truth).</li>
 * </ul>
 */
@Entity
@Table(
        name = "appointments",
        indexes = {
                @Index(name = "idx_appointments_appointment_date", columnList = "appointment_date"),
                @Index(name = "idx_appointments_doctor_date", columnList = "doctor_id, appointment_date"),
                @Index(name = "idx_appointments_patient_date", columnList = "patient_id, appointment_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appointments_doctor_slot",
                        columnNames = {"doctor_id", "appointment_date"}
                )
        }
)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "doctor_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_appointments_doctor")
    )
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "patient_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_appointments_patient")
    )
    private User patient;

    @Column(name = "appointment_date", nullable = false)
    private Instant appointmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 2000)
    private String notes;

    /**
     * Optimistic locking. Two concurrent updates of the same appointment cannot
     * silently overwrite each other; the second one fails and is retried by the
     * client. This is part of the ACID story of the project.
     */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Appointment() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public User getPatient() {
        return patient;
    }

    public void setPatient(User patient) {
        this.patient = patient;
    }

    public Instant getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(Instant appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
