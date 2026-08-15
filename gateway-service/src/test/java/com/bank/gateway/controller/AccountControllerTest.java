package com.bank.gateway.controller;

import com.bank.gateway.dto.AccountResponse;
import com.bank.gateway.dto.TransactionResponse;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.exception.GlobalExceptionHandler;
import com.bank.gateway.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new AccountController(accountService))
                .controllerAdvice(new GlobalExceptionHandler())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken("1", null, List.of()))))
                .build();
    }

    @Test
    void getMyAccountReturnsTheCallersAccount() {
        when(accountService.getMyAccount(1L)).thenReturn(
                Mono.just(new AccountResponse(10L, "DE001", new BigDecimal("925.00"), "USD", "ACTIVE")));

        webTestClient.get().uri("/api/v1/accounts/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(10)
                .jsonPath("$.iban").isEqualTo("DE001");
    }

    @Test
    void getAccountReturns404WhenTheServiceReportsNotFound() {
        when(accountService.getAccount(eq(1L), eq(99L))).thenReturn(Mono.error(new AccountNotFoundException(99L)));

        webTestClient.get().uri("/api/v1/accounts/99")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Account Not Found");
    }

    @Test
    void getTransactionsReturnsTheCallersHistory() {
        TransactionResponse transaction = new TransactionResponse(
                4L, "SENT", new BigDecimal("75.00"), "Bob Smith", "DE002", Instant.now(), "SUCCESS", null);
        when(accountService.getTransactions(1L, 10L)).thenReturn(Mono.just(List.of(transaction)));

        webTestClient.get().uri("/api/v1/accounts/10/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].type").isEqualTo("SENT")
                .jsonPath("$[0].counterpartyName").isEqualTo("Bob Smith");
    }
}
