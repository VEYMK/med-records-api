package com.example.medrecords.api;

import com.example.medrecords.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    void registerLoginAndReadCurrentUser() throws Exception {
        register("doctor@example.com", "Dr. House", Role.DOCTOR);

        String token = login("doctor@example.com");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("doctor@example.com"))
                .andExpect(jsonPath("$.role").value("DOCTOR"));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        register("doctor@example.com", "Dr. House", Role.DOCTOR);

        String payload = objectMapper.writeValueAsString(Map.of(
                "email", "doctor@example.com",
                "password", "wrong-password"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        register("doctor@example.com", "Dr. House", Role.DOCTOR);

        String payload = objectMapper.writeValueAsString(Map.of(
                "email", "doctor@example.com",
                "password", "password123",
                "fullName", "Another Doctor",
                "role", "DOCTOR"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsReturnBadRequest() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "email", "not-an-email",
                "password", "short",
                "fullName", "",
                "role", "DOCTOR"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }
}
