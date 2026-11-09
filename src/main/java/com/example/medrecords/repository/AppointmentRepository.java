package com.example.medrecords.repository;

import com.example.medrecords.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByDoctorIdOrderByAppointmentDateAsc(UUID doctorId);

    List<Appointment> findByPatientIdOrderByAppointmentDateAsc(UUID patientId);

    boolean existsByDoctorIdAndAppointmentDate(UUID doctorId, Instant appointmentDate);
}
