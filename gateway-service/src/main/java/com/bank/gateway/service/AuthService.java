package com.bank.gateway.service;

import com.bank.gateway.domain.User;
import com.bank.gateway.dto.ChangePasswordRequest;
import com.bank.gateway.dto.LoginRequest;
import com.bank.gateway.dto.LoginResponse;
import com.bank.gateway.dto.RegisterRequest;
import com.bank.gateway.dto.RegisterResponse;
import com.bank.gateway.exception.InvalidCredentialsException;
import com.bank.gateway.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

/**
 * Every repository call here is blocking JDBC (see RegistrationExecutor / com.bank.gateway.domain
 * package docs for why gateway talks to the shared DB directly at all), so each is wrapped in
 * subscribeOn(Schedulers.boundedElastic()) to keep it off gateway's Netty event-loop threads.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistrationExecutor registrationExecutor;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RegistrationExecutor registrationExecutor) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registrationExecutor = registrationExecutor;
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        return Mono.fromCallable(() -> registrationExecutor.execute(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<LoginResponse> login(LoginRequest request) {
        return Mono.fromCallable(() -> loginBlocking(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LoginResponse loginBlocking(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), List.of("USER"));
        Instant expiresAt = Instant.now().plusSeconds(jwtService.getExpirationSeconds());
        return new LoginResponse(token, "Bearer", expiresAt);
    }

    public Mono<Void> changePassword(String authorizationHeader, ChangePasswordRequest request) {
        return Mono.<Void>fromRunnable(() -> changePasswordBlocking(authorizationHeader, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void changePasswordBlocking(String authorizationHeader, ChangePasswordRequest request) {
        Claims claims = jwtService.parseAndValidate(extractToken(authorizationHeader));
        Long userId = Long.valueOf(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired token"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or malformed Authorization header");
        }
        return authorizationHeader.substring("Bearer ".length());
    }
}
