package com.example.medrecords.dto.appointment;

import com.example.medrecords.domain.Appointment;
import com.example.medrecords.domain.AppointmentStatus;
import com.example.medrecords.domain.User;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UserSummary doctor,
        UserSummary patient,
        Instant appointmentDate,
        AppointmentStatus status,
        String reason,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                UserSummary.from(appointment.getDoctor()),
                UserSummary.from(appointment.getPatient()),
                appointment.getAppointmentDate(),
                appointment.getStatus(),
                appointment.getReason(),
                appointment.getNotes(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }

    public record UserSummary(UUID id, String fullName, String email) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getFullName(), user.getEmail());
        }
    }
}
