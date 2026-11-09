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
import com.example.medrecords.repository.AppointmentRepository;
import com.example.medrecords.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private UserRepository users;

    @InjectMocks
    private AppointmentService service;

    private User doctor;
    private User patient;
    private Instant date;

    @BeforeEach
    void setUp() {
        doctor = user(Role.DOCTOR, "doctor@example.com", "Dr. House");
        patient = user(Role.PATIENT, "patient@example.com", "John Doe");
        date = Instant.parse("2030-01-15T10:00:00Z");
    }

    @Test
    void createsAppointmentSuccessfully() {
        when(users.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(users.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(appointments.existsByDoctorIdAndAppointmentDate(doctor.getId(), date)).thenReturn(false);
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(UUID.randomUUID());
            return appointment;
        });

        AppointmentResponse response = service.create(
                doctor.getId(),
                new CreateAppointmentRequest(patient.getId(), date, "Annual checkup", "Fasting")
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.doctor().email()).isEqualTo("doctor@example.com");
        assertThat(response.patient().email()).isEqualTo("patient@example.com");
        assertThat(response.appointmentDate()).isEqualTo(date);
        verify(appointments).saveAndFlush(any(Appointment.class));
    }

    @Test
    void rejectsTargetUserThatIsNotAPatient() {
        User anotherDoctor = user(Role.DOCTOR, "other@example.com", "Dr. Other");
        when(users.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(users.findById(anotherDoctor.getId())).thenReturn(Optional.of(anotherDoctor));

        assertThatThrownBy(() -> service.create(
                doctor.getId(),
                new CreateAppointmentRequest(anotherDoctor.getId(), date, "Checkup", null)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a patient");

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void rejectsDoubleBooking() {
        when(users.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(users.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(appointments.existsByDoctorIdAndAppointmentDate(doctor.getId(), date)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                doctor.getId(),
                new CreateAppointmentRequest(patient.getId(), date, "Checkup", null)
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already booked");

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void onlyAssignedDoctorCanChangeStatus() {
        Appointment appointment = appointment(doctor, patient, date, AppointmentStatus.SCHEDULED);
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.updateStatus(
                UUID.randomUUID(),
                appointment.getId(),
                AppointmentStatus.COMPLETED
        ))
                .isInstanceOf(ForbiddenException.class);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void patientCanCancelOwnScheduledAppointment() {
        Appointment appointment = appointment(doctor, patient, date, AppointmentStatus.SCHEDULED);
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        AppointmentResponse response = service.cancel(patient.getId(), appointment.getId());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cannotCancelAlreadyCompletedAppointment() {
        Appointment appointment = appointment(doctor, patient, date, AppointmentStatus.COMPLETED);
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel(patient.getId(), appointment.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private static User user(Role role, String email, String fullName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setPasswordHash("hash");
        user.setEnabled(true);
        return user;
    }

    private static Appointment appointment(
            User doctor,
            User patient,
            Instant date,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setAppointmentDate(date);
        appointment.setStatus(status);
        appointment.setReason("Checkup");
        return appointment;
    }
}
