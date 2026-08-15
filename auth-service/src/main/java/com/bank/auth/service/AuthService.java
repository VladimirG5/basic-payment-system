package com.bank.auth.service;

import com.bank.auth.dto.ChangePasswordRequest;
import com.bank.auth.dto.CreateUserRequest;
import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.LoginResponse;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.RegisterResponse;
import com.bank.auth.exception.CoreServiceException;
import com.bank.auth.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Owns password hashing/verification and JWT issuance; all persistence goes through
 * CoreUserClient since this service has no database of its own. A core-side 404 on the
 * login/change-password lookups is deliberately remapped to InvalidCredentialsException rather
 * than left as a passthrough 404 - callers must not be able to tell "no such account" apart
 * from "wrong password" (the same property today's gateway AuthService already has via
 * userRepository.findByEmail(...).orElseThrow(...)).
 */
@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CoreUserClient coreUserClient;

    public AuthService(PasswordEncoder passwordEncoder, JwtService jwtService, CoreUserClient coreUserClient) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.coreUserClient = coreUserClient;
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        CreateUserRequest createUserRequest = new CreateUserRequest(
                request.fullName(), request.email(), passwordEncoder.encode(request.password()));
        return coreUserClient.createUser(createUserRequest);
    }

    public Mono<LoginResponse> login(LoginRequest request) {
        return coreUserClient.findByEmail(request.email())
                .onErrorResume(CoreServiceException.class, ex -> notFoundToInvalidCredentials(ex, "Invalid email or password"))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
                        return Mono.error(new InvalidCredentialsException("Invalid email or password"));
                    }
                    String token = jwtService.generateToken(user.userId(), user.email(), List.of("USER"));
                    Instant expiresAt = Instant.now().plusSeconds(jwtService.getExpirationSeconds());
                    return Mono.just(new LoginResponse(token, "Bearer", expiresAt));
                });
    }

    public Mono<Void> changePassword(String authorizationHeader, ChangePasswordRequest request) {
        // extractUserId can throw (missing/invalid/expired token); deferring it into the chain
        // via fromCallable keeps that a Mono error signal instead of a synchronous throw, which
        // would otherwise be the one code path in this class that doesn't behave reactively.
        return Mono.fromCallable(() -> extractUserId(authorizationHeader))
                .flatMap(userId -> coreUserClient.findById(userId)
                        .onErrorResume(CoreServiceException.class, ex -> notFoundToInvalidCredentials(ex, "Invalid or expired token"))
                        .flatMap(user -> {
                            if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
                                return Mono.error(new InvalidCredentialsException("Old password is incorrect"));
                            }
                            return coreUserClient.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
                        }));
    }

    private <T> Mono<T> notFoundToInvalidCredentials(CoreServiceException ex, String message) {
        if (ex.getStatus() == HttpStatus.NOT_FOUND) {
            return Mono.error(new InvalidCredentialsException(message));
        }
        return Mono.error(ex);
    }

    private Long extractUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or malformed Authorization header");
        }
        Claims claims = jwtService.parseAndValidate(authorizationHeader.substring("Bearer ".length()));
        return Long.valueOf(claims.getSubject());
    }
}
