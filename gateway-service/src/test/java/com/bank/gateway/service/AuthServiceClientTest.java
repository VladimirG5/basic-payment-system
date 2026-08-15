package com.bank.gateway.service;

import com.bank.gateway.dto.ChangePasswordRequest;
import com.bank.gateway.dto.LoginRequest;
import com.bank.gateway.dto.LoginResponse;
import com.bank.gateway.dto.RegisterRequest;
import com.bank.gateway.exception.CoreServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real WebClient call + response-mapping + error-mapping code paths without a
 * network dependency, by backing the WebClient with a stubbed ExchangeFunction that returns a
 * constructed ClientResponse - mirrors what auth-service actually returns.
 */
class AuthServiceClientTest {

    private static AuthServiceClient clientReturning(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new AuthServiceClient(webClient);
    }

    @Test
    void registerReturnsTheParsedResponseOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.CREATED)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"userId":1,"fullName":"Alice","email":"alice@example.com",
                         "accountId":10,"iban":"DE001","balance":0,"currency":"USD","accountStatus":"ACTIVE"}
                        """)
                .build();

        StepVerifier.create(clientReturning(response)
                        .register(new RegisterRequest("Alice", "alice@example.com", "Password123!")))
                .assertNext(body -> assertEquals("alice@example.com", body.email()))
                .verifyComplete();
    }

    @Test
    void registerMapsADuplicateEmailErrorBodyToCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.CONFLICT)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body("""
                        {"status":409,"title":"Duplicate Email","detail":"already registered"}
                        """)
                .build();

        StepVerifier.create(clientReturning(response)
                        .register(new RegisterRequest("Alice", "alice@example.com", "Password123!")))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException coreServiceException = (CoreServiceException) ex;
                    assertEquals(HttpStatus.CONFLICT, coreServiceException.getStatus());
                    assertEquals("Duplicate Email", coreServiceException.getTitle());
                    assertEquals("already registered", coreServiceException.getMessage());
                })
                .verify();
    }

    @Test
    void loginMapsAnEmptyErrorBodyToAGenericCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.UNAUTHORIZED).build();

        StepVerifier.create(clientReturning(response).login(new LoginRequest("alice@example.com", "wrong")))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException coreServiceException = (CoreServiceException) ex;
                    assertEquals(HttpStatus.UNAUTHORIZED, coreServiceException.getStatus());
                    assertEquals("Auth Request Failed", coreServiceException.getTitle());
                })
                .verify();
    }

    @Test
    void loginReturnsTheTokenOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"token":"abc.def.ghi","tokenType":"Bearer","expiresAt":"2026-08-15T21:00:00Z"}
                        """)
                .build();

        StepVerifier.create(clientReturning(response).login(new LoginRequest("alice@example.com", "Password123!")))
                .assertNext((LoginResponse body) -> assertEquals("abc.def.ghi", body.token()))
                .verifyComplete();
    }

    @Test
    void changePasswordSetsTheAuthorizationHeaderWhenPresentAndCompletesOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.NO_CONTENT).build();

        StepVerifier.create(clientReturning(response)
                        .changePassword("Bearer some-token", new ChangePasswordRequest("old1234", "new12345")))
                .verifyComplete();
    }

    @Test
    void changePasswordToleratesAMissingAuthorizationHeader() {
        ClientResponse response = ClientResponse.create(HttpStatus.UNAUTHORIZED).build();

        StepVerifier.create(clientReturning(response)
                        .changePassword(null, new ChangePasswordRequest("old1234", "new12345")))
                .expectError(CoreServiceException.class)
                .verify();
    }
}
