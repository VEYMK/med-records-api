package com.example.medrecords.service;

import com.example.medrecords.domain.Role;
import com.example.medrecords.domain.User;
import com.example.medrecords.dto.auth.AuthResponse;
import com.example.medrecords.dto.auth.LoginRequest;
import com.example.medrecords.dto.auth.RegisterRequest;
import com.example.medrecords.dto.auth.UserResponse;
import com.example.medrecords.exception.ConflictException;
import com.example.medrecords.exception.UnauthorizedException;
import com.example.medrecords.repository.UserRepository;
import com.example.medrecords.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService tokenService;

    @InjectMocks
    private AuthService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setFullName("Dr. House");
        user.setRole(Role.DOCTOR);
        user.setPasswordHash("bcrypt-hash");
        user.setEnabled(true);
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(users.existsByEmailIgnoreCase("doctor@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(user.getId());
            return saved;
        });

        UserResponse response = service.register(new RegisterRequest(
                "  Doctor@Example.com ",
                "password123",
                " Dr. House ",
                Role.DOCTOR
        ));

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("doctor@example.com");
        assertThat(response.fullName()).isEqualTo("Dr. House");
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
        verify(passwordEncoder).encode("password123");
        verify(users).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("doctor@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "doctor@example.com",
                "password123",
                "Dr. House",
                Role.DOCTOR
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");

        verify(users, never()).save(any(User.class));
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> service.login(new LoginRequest(
                "doctor@example.com",
                "wrong-password"
        )))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(users.findByEmailIgnoreCase("doctor@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(new JwtTokenService.IssuedToken("jwt-value", 3600));

        AuthResponse response = service.login(new LoginRequest("doctor@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.user().email()).isEqualTo("doctor@example.com");
    }
}
