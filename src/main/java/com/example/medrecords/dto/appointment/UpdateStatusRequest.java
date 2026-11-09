package com.example.medrecords.dto.appointment;

import com.example.medrecords.domain.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull AppointmentStatus status
) {
}
