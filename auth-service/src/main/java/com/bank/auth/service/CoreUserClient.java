package com.bank.auth.service;

import com.bank.auth.dto.CreateUserRequest;
import com.bank.auth.dto.RegisterResponse;
import com.bank.auth.dto.UpdatePasswordRequest;
import com.bank.auth.dto.UserCredentials;
import com.bank.auth.exception.CoreServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls payment-core-service's internal /internal/users* API - auth-service has no database
 * connection of its own. Error bodies come back as RFC 7807 ProblemDetail (core's own
 * GlobalExceptionHandler) and are re-thrown as CoreServiceException, exactly mirroring
 * gateway-service's PaymentCoreClient.
 */
@Component
public class CoreUserClient {

    private final WebClient webClient;

    public CoreUserClient(WebClient coreServiceWebClient) {
        this.webClient = coreServiceWebClient;
    }

    public Mono<RegisterResponse> createUser(CreateUserRequest request) {
        return webClient.post()
                .uri("/internal/users")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(RegisterResponse.class);
    }

    public Mono<UserCredentials> findByEmail(String email) {
        return webClient.get()
                .uri("/internal/users/by-email/{email}", email)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(UserCredentials.class);
    }

    public Mono<UserCredentials> findById(Long userId) {
        return webClient.get()
                .uri("/internal/users/{userId}", userId)
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(UserCredentials.class);
    }

    public Mono<Void> updatePassword(Long userId, String passwordHash) {
        return webClient.patch()
                .uri("/internal/users/{userId}/password", userId)
                .header("X-User-Id", String.valueOf(userId))
                .bodyValue(new UpdatePasswordRequest(passwordHash))
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
                        problem.getTitle() != null ? problem.getTitle() : "User Lookup Failed",
                        problem.getDetail() != null ? problem.getDetail() : "User lookup failed"))
                .defaultIfEmpty(new CoreServiceException(status, "User Lookup Failed", "User lookup failed"));
    }
}
