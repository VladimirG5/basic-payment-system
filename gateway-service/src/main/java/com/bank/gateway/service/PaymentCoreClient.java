package com.bank.gateway.service;

import com.bank.gateway.dto.InternalTransferRequest;
import com.bank.gateway.dto.InternalTransferResult;
import com.bank.gateway.exception.CoreServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Proxies confirmed transfers to payment-core-service's internal endpoint. Error bodies come
 * back as RFC 7807 ProblemDetail (core's own GlobalExceptionHandler) and are re-thrown as
 * CoreServiceException carrying the same status/title/detail, so gateway's GlobalExceptionHandler
 * can pass them through to the client unchanged.
 */
@Component
public class PaymentCoreClient {

    private final WebClient webClient;

    public PaymentCoreClient(WebClient coreServiceWebClient) {
        this.webClient = coreServiceWebClient;
    }

    public Mono<InternalTransferResult> executeTransfer(InternalTransferRequest request) {
        return webClient.post()
                .uri("/internal/transfers")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toCoreServiceException)
                .bodyToMono(InternalTransferResult.class);
    }

    private Mono<? extends Throwable> toCoreServiceException(ClientResponse response) {
        HttpStatus status = HttpStatus.valueOf(response.statusCode().value());
        return response.bodyToMono(ProblemDetail.class)
                .map(problem -> new CoreServiceException(
                        status,
                        problem.getTitle() != null ? problem.getTitle() : "Transfer Failed",
                        problem.getDetail() != null ? problem.getDetail() : "Transfer failed"))
                .defaultIfEmpty(new CoreServiceException(status, "Transfer Failed", "Transfer failed"));
    }
}
