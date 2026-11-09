package com.example.medrecords.dto.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull @Future Instant appointmentDate,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 2000) String notes
) {
}
