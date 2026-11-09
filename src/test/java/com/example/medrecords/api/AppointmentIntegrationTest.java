package com.example.medrecords.api;

import com.example.medrecords.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentIntegrationTest extends IntegrationTestSupport {

    private static final Instant SLOT = Instant.parse("2030-01-15T10:00:00Z");

    @Test
    void doctorBooksAppointmentAndPatientSeesIt() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        String doctorToken = login("doctor@example.com");
        String patientToken = login("patient@example.com");

        String appointmentId = createAppointment(doctorToken, patientId, SLOT);

        mockMvc.perform(get("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointmentId))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void doctorCanCompleteAppointment() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        String doctorToken = login("doctor@example.com");
        String appointmentId = createAppointment(doctorToken, patientId, SLOT);

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void patientCannotChangeStatus() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        String doctorToken = login("doctor@example.com");
        String patientToken = login("patient@example.com");
        String appointmentId = createAppointment(doctorToken, patientId, SLOT);

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doubleBookingReturnsConflict() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        String doctorToken = login("doctor@example.com");
        createAppointment(doctorToken, patientId, SLOT);

        String payload = appointmentPayload(patientId, SLOT);
        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void patientCanCancelAppointment() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        String doctorToken = login("doctor@example.com");
        String patientToken = login("patient@example.com");
        String appointmentId = createAppointment(doctorToken, patientId, SLOT);

        mockMvc.perform(delete("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void anotherPatientCannotReadForeignAppointment() throws Exception {
        UUID patientId = register("patient@example.com", "John Doe", Role.PATIENT);
        register("doctor@example.com", "Dr. House", Role.DOCTOR);
        register("other@example.com", "Jane Roe", Role.PATIENT);
        String doctorToken = login("doctor@example.com");
        String otherPatientToken = login("other@example.com");
        String appointmentId = createAppointment(doctorToken, patientId, SLOT);

        mockMvc.perform(get("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherPatientToken)))
                .andExpect(status().isForbidden());
    }

    private String createAppointment(String doctorToken, UUID patientId, Instant slot) throws Exception {
        String response = mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(patientId, slot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    private String appointmentPayload(UUID patientId, Instant slot) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "patientId", patientId.toString(),
                "appointmentDate", slot.toString(),
                "reason", "Annual checkup",
                "notes", "Fasting before the visit"
        ));
    }
}
