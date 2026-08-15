package com.bank.auth.service;

import com.bank.auth.audit.AuditLogger;
import com.bank.auth.dto.ChangePasswordRequest;
import com.bank.auth.dto.CreateUserRequest;
import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.LoginResponse;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.RegisterResponse;
import com.bank.auth.dto.UserCredentials;
import com.bank.auth.exception.CoreServiceException;
import com.bank.auth.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests - CoreUserClient is mocked, so this never touches the network or a database.
 * The login/change-password 404-to-InvalidCredentialsException remapping is the property worth
 * the most scrutiny here: callers must not be able to distinguish "no such account" from
 * "wrong password".
 */
class AuthServiceTest {

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private CoreUserClient coreUserClient;
    private AuditLogger auditLogger;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        coreUserClient = mock(CoreUserClient.class);
        auditLogger = mock(AuditLogger.class);
        authService = new AuthService(passwordEncoder, jwtService, coreUserClient, auditLogger);
    }

    @Test
    void registerHashesThePasswordBeforeSendingItToCore() {
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        RegisterResponse coreResponse = new RegisterResponse(1L, "Ada Lovelace", "ada@example.com",
                1L, "DE00", BigDecimal.ZERO, "USD", "ACTIVE");
        when(coreUserClient.createUser(any())).thenReturn(Mono.just(coreResponse));

        StepVerifier.create(authService.register(new RegisterRequest("Ada Lovelace", "ada@example.com", "plaintext")))
                .expectNext(coreResponse)
                .verifyComplete();

        verify(coreUserClient).createUser(new CreateUserRequest("Ada Lovelace", "ada@example.com", "hashed"));
    }

    @Test
    void registerPropagatesADuplicateEmailAsIs() {
        CoreServiceException conflict = new CoreServiceException(HttpStatus.CONFLICT, "Email Already Registered", "taken");
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(coreUserClient.createUser(any())).thenReturn(Mono.error(conflict));

        StepVerifier.create(authService.register(new RegisterRequest("Ada", "ada@example.com", "plaintext")))
                .expectErrorMatches(ex -> ex == conflict)
                .verify();
    }

    @Test
    void loginIssuesATokenOnAMatchingPassword() {
        UserCredentials user = new UserCredentials(1L, "Ada Lovelace", "ada@example.com", "hashed");
        when(coreUserClient.findByEmail("ada@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("plaintext", "hashed")).thenReturn(true);
        when(jwtService.generateToken(1L, "ada@example.com", java.util.List.of("USER"))).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        StepVerifier.create(authService.login(new LoginRequest("ada@example.com", "plaintext")))
                .assertNext(response -> {
                    assertEquals("jwt-token", response.token());
                    assertEquals("Bearer", response.tokenType());
                })
                .verifyComplete();
    }

    @Test
    void loginRejectsAWrongPasswordAsInvalidCredentials() {
        UserCredentials user = new UserCredentials(1L, "Ada Lovelace", "ada@example.com", "hashed");
        when(coreUserClient.findByEmail("ada@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        StepVerifier.create(authService.login(new LoginRequest("ada@example.com", "wrong")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void loginRemapsAnUnknownEmailToInvalidCredentialsRatherThanLeakingNotFound() {
        CoreServiceException notFound = new CoreServiceException(HttpStatus.NOT_FOUND, "User Not Found", "gone");
        when(coreUserClient.findByEmail("nobody@example.com")).thenReturn(Mono.error(notFound));

        StepVerifier.create(authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void changePasswordUpdatesTheHashWhenTheOldPasswordMatches() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        UserCredentials user = new UserCredentials(1L, "Ada Lovelace", "ada@example.com", "old-hash");
        when(coreUserClient.findById(1L)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");
        when(coreUserClient.updatePassword(1L, "new-hash")).thenReturn(Mono.empty());

        StepVerifier.create(authService.changePassword("Bearer valid-token", new ChangePasswordRequest("old-pass", "new-pass")))
                .verifyComplete();

        verify(coreUserClient).updatePassword(1L, "new-hash");
    }

    @Test
    void changePasswordRejectsAWrongOldPassword() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        UserCredentials user = new UserCredentials(1L, "Ada Lovelace", "ada@example.com", "old-hash");
        when(coreUserClient.findById(1L)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        StepVerifier.create(authService.changePassword("Bearer valid-token", new ChangePasswordRequest("wrong", "new-pass")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void changePasswordRejectsAMissingAuthorizationHeader() {
        StepVerifier.create(authService.changePassword(null, new ChangePasswordRequest("old", "new-pass")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
