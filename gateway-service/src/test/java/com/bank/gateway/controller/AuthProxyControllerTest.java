package com.bank.gateway.controller;

import com.bank.gateway.dto.LoginResponse;
import com.bank.gateway.dto.RegisterResponse;
import com.bank.gateway.exception.CoreServiceException;
import com.bank.gateway.exception.GlobalExceptionHandler;
import com.bank.gateway.service.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthProxyControllerTest {

    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new AuthProxyController(authServiceClient))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerReturns201OnSuccess() {
        when(authServiceClient.register(any())).thenReturn(Mono.just(
                new RegisterResponse(1L, "Alice", "alice@example.com", 10L, "DE001", BigDecimal.ZERO, "USD", "ACTIVE")));

        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"fullName":"Alice","email":"alice@example.com","password":"Password123!"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.email").isEqualTo("alice@example.com");
    }

    @Test
    void registerRejectsAnInvalidRequestBodyWithAValidationProblem() {
        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"fullName":"","email":"not-an-email","password":"short"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation Failed");
    }

    @Test
    void registerPropagatesADuplicateEmailAsAConflict() {
        when(authServiceClient.register(any())).thenReturn(
                Mono.error(new CoreServiceException(HttpStatus.CONFLICT, "Duplicate Email", "already registered")));

        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"fullName":"Alice","email":"alice@example.com","password":"Password123!"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Duplicate Email");
    }

    @Test
    void loginReturnsTheTokenOnSuccess() {
        when(authServiceClient.login(any())).thenReturn(
                Mono.just(new LoginResponse("abc.def.ghi", "Bearer", Instant.now().plusSeconds(3600))));

        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"alice@example.com","password":"Password123!"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("abc.def.ghi");
    }

    @Test
    void changePasswordReturns204AndForwardsTheAuthorizationHeader() {
        when(authServiceClient.changePassword(eq("Bearer some-token"), any())).thenReturn(Mono.empty());

        webTestClient.post().uri("/api/v1/auth/change-password")
                .header("Authorization", "Bearer some-token")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"oldPassword":"old12345","newPassword":"new12345"}
                        """)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void changePasswordToleratesAMissingAuthorizationHeader() {
        when(authServiceClient.changePassword(isNull(), any())).thenReturn(
                Mono.error(new CoreServiceException(HttpStatus.UNAUTHORIZED, "Invalid Credentials", "missing token")));

        webTestClient.post().uri("/api/v1/auth/change-password")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"oldPassword":"old12345","newPassword":"new12345"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
