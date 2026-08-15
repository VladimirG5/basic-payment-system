package com.bank.gateway.service;

import com.bank.gateway.dto.InternalTransferRequest;
import com.bank.gateway.exception.CoreServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentCoreClientTest {

    private static PaymentCoreClient clientReturning(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new PaymentCoreClient(webClient);
    }

    private static InternalTransferRequest sampleRequest() {
        return new InternalTransferRequest(10L, 20L, new BigDecimal("75.00"), "USD", "test");
    }

    @Test
    void executeTransferReturnsTheParsedResultOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"transactionId":4,"sourceAccountId":10,"destinationAccountId":20,
                         "sourceNewBalance":925.00,"destinationNewBalance":75.00,"amount":75.00,
                         "currency":"USD","status":"SUCCESS","createdAt":"2026-08-15T21:00:00Z"}
                        """)
                .build();

        StepVerifier.create(clientReturning(response).executeTransfer(sampleRequest(), 1L))
                .assertNext(result -> {
                    assertEquals(4L, result.transactionId());
                    assertEquals(new BigDecimal("925.00"), result.sourceNewBalance());
                })
                .verifyComplete();
    }

    @Test
    void executeTransferMapsAnInsufficientFundsErrorBodyToCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body("""
                        {"status":422,"title":"Insufficient Funds","detail":"not enough balance"}
                        """)
                .build();

        StepVerifier.create(clientReturning(response).executeTransfer(sampleRequest(), 1L))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException coreServiceException = (CoreServiceException) ex;
                    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, coreServiceException.getStatus());
                    assertEquals("Insufficient Funds", coreServiceException.getTitle());
                    assertEquals("not enough balance", coreServiceException.getMessage());
                })
                .verify();
    }

    @Test
    void executeTransferMapsAnEmptyErrorBodyToAGenericCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build();

        StepVerifier.create(clientReturning(response).executeTransfer(sampleRequest(), 1L))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException coreServiceException = (CoreServiceException) ex;
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, coreServiceException.getStatus());
                    assertEquals("Transfer Failed", coreServiceException.getTitle());
                })
                .verify();
    }
}
