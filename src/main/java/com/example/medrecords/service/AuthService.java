package com.example.medrecords.service;

import com.example.medrecords.domain.User;
import com.example.medrecords.dto.auth.AuthResponse;
import com.example.medrecords.dto.auth.LoginRequest;
import com.example.medrecords.dto.auth.RegisterRequest;
import com.example.medrecords.dto.auth.UserResponse;
import com.example.medrecords.exception.ConflictException;
import com.example.medrecords.exception.NotFoundException;
import com.example.medrecords.exception.UnauthorizedException;
import com.example.medrecords.repository.UserRepository;
import com.example.medrecords.security.JwtTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService tokenService
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /**
     * Registers a doctor or a patient. The password is hashed with BCrypt; the
     * plaintext value never reaches the database.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setEnabled(true);

        return UserResponse.from(users.save(user));
    }

    /**
     * Verifies credentials through Spring Security's {@link AuthenticationManager}
     * and then issues a JWT. This keeps password checking inside the framework
     * (including timing-safe BCrypt comparison) instead of hand-rolling it.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return new AuthResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                UserResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
