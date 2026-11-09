package com.example.medrecords.service;

import com.example.medrecords.domain.Appointment;
import com.example.medrecords.domain.AppointmentStatus;
import com.example.medrecords.domain.Role;
import com.example.medrecords.domain.User;
import com.example.medrecords.dto.appointment.AppointmentResponse;
import com.example.medrecords.dto.appointment.CreateAppointmentRequest;
import com.example.medrecords.exception.BadRequestException;
import com.example.medrecords.exception.ConflictException;
import com.example.medrecords.exception.ForbiddenException;
import com.example.medrecords.exception.NotFoundException;
import com.example.medrecords.repository.AppointmentRepository;
import com.example.medrecords.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final UserRepository users;

    public AppointmentService(AppointmentRepository appointments, UserRepository users) {
        this.appointments = appointments;
        this.users = users;
    }

    /**
     * Only a doctor can book an appointment, and only for an existing patient.
     * The unique constraint {@code uk_appointments_doctor_slot} is the final
     * guard against double booking: the application check gives a nice error,
     * the database check keeps the data correct under concurrency.
     */
    @Transactional
    public AppointmentResponse create(UUID doctorId, CreateAppointmentRequest request) {
        User doctor = users.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));
        if (doctor.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only a doctor can create appointments");
        }

        User patient = users.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Patient not found"));
        if (patient.getRole() != Role.PATIENT) {
            throw new BadRequestException("The selected user is not a patient");
        }

        if (appointments.existsByDoctorIdAndAppointmentDate(doctorId, request.appointmentDate())) {
            throw new ConflictException("This time slot is already booked");
        }

        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setReason(request.reason().trim());
        appointment.setNotes(request.notes());
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        try {
            return AppointmentResponse.from(appointments.saveAndFlush(appointment));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("This time slot is already booked");
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForUser(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<Appointment> result = switch (user.getRole()) {
            case DOCTOR -> appointments.findByDoctorIdOrderByAppointmentDateAsc(userId);
            case PATIENT -> appointments.findByPatientIdOrderByAppointmentDateAsc(userId);
        };

        return result.stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID userId, UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        assertParticipant(userId, appointment);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(
            UUID doctorId,
            UUID appointmentId,
            AppointmentStatus newStatus
    ) {
        Appointment appointment = load(appointmentId);
        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new ForbiddenException("Only the assigned doctor can change the status");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled appointments can be updated");
        }
        if (newStatus == AppointmentStatus.SCHEDULED) {
            throw new BadRequestException("Status must be COMPLETED or CANCELLED");
        }

        appointment.setStatus(newStatus);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(UUID patientId, UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new ForbiddenException("Only the patient can cancel this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled appointments can be cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return AppointmentResponse.from(appointment);
    }

    private Appointment load(UUID appointmentId) {
        return appointments.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
    }

    private static void assertParticipant(UUID userId, Appointment appointment) {
        boolean isDoctor = appointment.getDoctor().getId().equals(userId);
        boolean isPatient = appointment.getPatient().getId().equals(userId);
        if (!isDoctor && !isPatient) {
            throw new ForbiddenException("You do not have access to this appointment");
        }
    }
}
