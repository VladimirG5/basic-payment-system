package com.bank.auth.controller;

import com.bank.auth.dto.ChangePasswordRequest;
import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.LoginResponse;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.RegisterResponse;
import com.bank.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Controller bound directly to WebTestClient with a mocked AuthService - no Spring context, just
 * the routing/status-code/body-mapping this class is actually responsible for.
 */
class AuthControllerTest {

    private AuthService authService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        client = WebTestClient.bindToController(new AuthController(authService)).build();
    }

    @Test
    void registerReturns201WithTheCoreResponseBody() {
        RegisterResponse response = new RegisterResponse(1L, "Ada Lovelace", "ada@example.com",
                1L, "DE00", BigDecimal.ZERO, "USD", "ACTIVE");
        when(authService.register(any())).thenReturn(Mono.just(response));

        client.post().uri("/api/v1/auth/register")
                .bodyValue(new RegisterRequest("Ada Lovelace", "ada@example.com", "plaintext1"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(1)
                .jsonPath("$.email").isEqualTo("ada@example.com");
    }

    @Test
    void loginReturns200WithATokenBody() {
        LoginResponse response = new LoginResponse("jwt-token", "Bearer", Instant.now());
        when(authService.login(any())).thenReturn(Mono.just(response));

        client.post().uri("/api/v1/auth/login")
                .bodyValue(new LoginRequest("ada@example.com", "plaintext1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("jwt-token")
                .jsonPath("$.tokenType").isEqualTo("Bearer");
    }

    @Test
    void changePasswordReturns204WithNoBody() {
        when(authService.changePassword(any(), any())).thenReturn(Mono.empty());

        client.post().uri("/api/v1/auth/change-password")
                .header("Authorization", "Bearer valid-token")
                .bodyValue(new ChangePasswordRequest("old-pass1", "new-pass1"))
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    void registerRejectsAnInvalidBodyWithoutCallingTheService() {
        client.post().uri("/api/v1/auth/register")
                .bodyValue(new RegisterRequest("", "not-an-email", "short"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
