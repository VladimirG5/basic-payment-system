package com.bank.gateway.service;

import com.bank.gateway.dto.ChangePasswordRequest;
import com.bank.gateway.dto.LoginRequest;
import com.bank.gateway.dto.LoginResponse;
import com.bank.gateway.dto.RegisterRequest;
import com.bank.gateway.dto.RegisterResponse;
import com.bank.gateway.exception.CoreServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies register/login/change-password to auth-service - gateway no longer handles auth
 * itself (see AuthProxyController). Error bodies come back as RFC 7807 ProblemDetail
 * (auth-service's own GlobalExceptionHandler) and are re-thrown as CoreServiceException,
 * exactly mirroring PaymentCoreClient's pattern for payment-core-service.
 */
@Component
public class AuthServiceClient {

    private final WebClient webClient;

    public AuthServiceClient(WebClient authServiceWebClient) {
        this.webClient = authServiceWebClient;
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        return webClient.post()
                .uri("/api/v1/auth/register")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(RegisterResponse.class);
    }

    public Mono<LoginResponse> login(LoginRequest request) {
        return webClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(LoginResponse.class);
    }

    public Mono<Void> changePassword(String authorizationHeader, ChangePasswordRequest request) {
        return webClient.post()
                .uri("/api/v1/auth/change-password")
                .headers(headers -> {
                    if (authorizationHeader != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .toBodilessEntity()
                .then();
    }

    private Mono<? extends Throwable> toCoreServiceException(ClientResponse response) {
        HttpStatus status = HttpStatus.valueOf(response.statusCode().value());
        return response.bodyToMono(ProblemDetail.class)
                .map(problem -> new CoreServiceException(
                        status,
                        problem.getTitle() != null ? problem.getTitle() : "Auth Request Failed",
                        problem.getDetail() != null ? problem.getDetail() : "Auth request failed"))
                .defaultIfEmpty(new CoreServiceException(status, "Auth Request Failed", "Auth request failed"));
    }
}
