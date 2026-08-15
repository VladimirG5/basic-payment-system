package com.bank.auth.service;

import com.bank.auth.dto.CreateUserRequest;
import com.bank.auth.exception.CoreServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real WebClient request/response pipeline (URI building, status-based error
 * mapping, JSON decoding) against a stubbed ExchangeFunction instead of a live
 * payment-core-service - no extra test dependency needed for a single-method fake.
 */
class CoreUserClientTest {

    private CoreUserClient clientReturning(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://core-test")
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new CoreUserClient(webClient);
    }

    @Test
    void createUserReturnsTheCoreResponseOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.CREATED)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"userId\":1,\"fullName\":\"Ada Lovelace\",\"email\":\"ada@example.com\","
                        + "\"accountId\":1,\"iban\":\"DE00\",\"balance\":0,\"currency\":\"USD\",\"accountStatus\":\"ACTIVE\"}")
                .build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.createUser(new CreateUserRequest("Ada Lovelace", "ada@example.com", "hashed")))
                .assertNext(result -> {
                    assertEquals(1L, result.userId());
                    assertEquals("ada@example.com", result.email());
                })
                .verifyComplete();
    }

    @Test
    void createUserMapsADuplicateEmailConflictToCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.CONFLICT)
                .header("Content-Type", "application/problem+json")
                .body("{\"title\":\"Email Already Registered\",\"detail\":\"taken\"}")
                .build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.createUser(new CreateUserRequest("Ada", "ada@example.com", "hashed")))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException cse = (CoreServiceException) ex;
                    assertEquals(HttpStatus.CONFLICT, cse.getStatus());
                    assertEquals("Email Already Registered", cse.getTitle());
                    assertEquals("taken", cse.getMessage());
                })
                .verify();
    }

    @Test
    void findByEmailReturnsCredentialsOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"userId\":1,\"fullName\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"passwordHash\":\"hashed\"}")
                .build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.findByEmail("ada@example.com"))
                .assertNext(user -> assertEquals("hashed", user.passwordHash()))
                .verifyComplete();
    }

    @Test
    void findByEmailMapsAnUnknownEmailNotFoundToCoreServiceException() {
        ClientResponse response = ClientResponse.create(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/problem+json")
                .body("{\"title\":\"User Not Found\",\"detail\":\"no such user\"}")
                .build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.findByEmail("nobody@example.com"))
                .expectErrorSatisfies(ex -> assertEquals(HttpStatus.NOT_FOUND, ((CoreServiceException) ex).getStatus()))
                .verify();
    }

    @Test
    void findByEmailFallsBackToADefaultMessageWhenTheErrorBodyIsEmpty() {
        ClientResponse response = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.findByEmail("ada@example.com"))
                .expectErrorSatisfies(ex -> {
                    CoreServiceException cse = (CoreServiceException) ex;
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, cse.getStatus());
                    assertEquals("User Lookup Failed", cse.getTitle());
                })
                .verify();
    }

    @Test
    void findByIdReturnsCredentialsOnSuccess() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"userId\":1,\"fullName\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"passwordHash\":\"hashed\"}")
                .build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.findById(1L))
                .assertNext(user -> assertEquals(1L, user.userId()))
                .verifyComplete();
    }

    @Test
    void updatePasswordCompletesOnA204() {
        ClientResponse response = ClientResponse.create(HttpStatus.NO_CONTENT).build();
        CoreUserClient client = clientReturning(response);

        StepVerifier.create(client.updatePassword(1L, "new-hash"))
                .verifyComplete();
    }
}
