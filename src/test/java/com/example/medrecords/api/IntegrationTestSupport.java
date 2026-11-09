package com.example.medrecords.api;

import com.example.medrecords.domain.Role;
import com.example.medrecords.repository.AppointmentRepository;
import com.example.medrecords.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for full-context integration tests. These tests boot the real
 * Spring context (security filters, JWT decoder, JPA, H2) and talk to the API
 * over MockMvc, so they cover authentication and persistence together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AppointmentRepository appointmentRepository;

    @BeforeEach
    void cleanDatabase() {
        appointmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected UUID register(String email, String fullName, Role role) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "password123",
                "fullName", fullName,
                "role", role.name()
        ));

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    protected String login(String email) throws Exception {
        return login(email, "password123");
    }

    protected String login(String email, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password
        ));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String authorization() {
        return HttpHeaders.AUTHORIZATION;
    }
}
