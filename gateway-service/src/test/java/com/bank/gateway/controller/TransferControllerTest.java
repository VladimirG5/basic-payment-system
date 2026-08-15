package com.bank.gateway.controller;

import com.bank.gateway.dto.TransferConfirmResponse;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.exception.GlobalExceptionHandler;
import com.bank.gateway.service.TransferConfirmService;
import com.bank.gateway.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferControllerTest {

    private final TransferService transferService = mock(TransferService.class);
    private final TransferConfirmService transferConfirmService = mock(TransferConfirmService.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new TransferController(transferService, transferConfirmService))
                .controllerAdvice(new GlobalExceptionHandler())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken("1", null, List.of()))))
                .build();
    }

    @Test
    void initiateReturnsTheChallengeOnSuccess() {
        when(transferService.initiate(eq(1L), any())).thenReturn(
                Mono.just(new TransferInitiateResponse("challenge-1", Instant.now().plusSeconds(180), "OTP_REQUIRED")));

        webTestClient.post().uri("/api/v1/transfers/initiate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sourceAccountId":10,"destinationAccountId":20,"amount":75.00,"currency":"USD"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.challengeId").isEqualTo("challenge-1");
    }

    @Test
    void initiateRejectsAnInvalidRequestBodyWithAValidationProblem() {
        webTestClient.post().uri("/api/v1/transfers/initiate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sourceAccountId":10,"destinationAccountId":20,"amount":-5,"currency":"US"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation Failed");
    }

    @Test
    void confirmRejectsARequestWithoutAnIdempotencyKey() {
        webTestClient.post().uri("/api/v1/transfers/confirm")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"challengeId":"challenge-1","otpCode":"123456"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Invalid Transfer");
    }

    @Test
    void confirmReturnsTheResultWhenAnIdempotencyKeyIsPresent() {
        when(transferConfirmService.confirm(eq(1L), eq("idem-key"), any())).thenReturn(
                Mono.just(new TransferConfirmResponse("SUCCESS", 4L, new BigDecimal("925.00"))));

        webTestClient.post().uri("/api/v1/transfers/confirm")
                .header("X-Idempotency-Key", "idem-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"challengeId":"challenge-1","otpCode":"123456"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.transactionId").isEqualTo(4);
    }
}
