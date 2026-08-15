package com.bank.core.controller;

import com.bank.core.dto.CreateUserRequest;
import com.bank.core.dto.UpdatePasswordRequest;
import com.bank.core.dto.UserCredentialsResult;
import com.bank.core.dto.UserProvisioningResult;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises InternalUserController over real HTTP (not just the service layer), since the
 * status-code mapping done by GlobalExceptionHandler is exactly what auth-service's
 * CoreUserClient depends on to distinguish "duplicate email" from "not found" from success.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalUserControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private Long createdUserId;

    @BeforeEach
    void useJdkHttpClientForPatchSupport() {
        // TestRestTemplate's default SimpleClientHttpRequestFactory (java.net.HttpURLConnection)
        // rejects PATCH outright; JdkClientHttpRequestFactory wraps java.net.http.HttpClient,
        // which supports it natively.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            accountRepository.findAll().stream()
                    .filter(account -> account.getUser().getId().equals(createdUserId))
                    .forEach(account -> accountRepository.deleteById(account.getId()));
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    void createUserProvisionsAZeroBalanceActiveAccountAtomically() {
        String email = uniqueEmail();

        ResponseEntity<UserProvisioningResult> response = restTemplate.postForEntity(
                url("/internal/users"),
                new CreateUserRequest("Test User", email, "bcrypt-hash"),
                UserProvisioningResult.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProvisioningResult body = response.getBody();
        assertNotNull(body);
        createdUserId = body.userId();

        assertEquals(email, body.email());
        assertNotNull(body.accountId());
        assertEquals(0, BigDecimal.ZERO.compareTo(body.balance()));
        assertEquals("ACTIVE", body.accountStatus());
        assertEquals("USD", body.currency());
    }

    @Test
    void createUserWithADuplicateEmailReturns409() {
        String email = uniqueEmail();
        UserProvisioningResult first = restTemplate.postForEntity(
                url("/internal/users"),
                new CreateUserRequest("First User", email, "bcrypt-hash"),
                UserProvisioningResult.class).getBody();
        createdUserId = first.userId();

        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                url("/internal/users"),
                new CreateUserRequest("Second User", email, "another-hash"),
                ProblemDetail.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void findByEmailForAnUnknownAddressReturns404() {
        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                url("/internal/users/by-email/does-not-exist@example.com"), ProblemDetail.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updatePasswordChangesTheStoredHashAndMissingUserReturns404() {
        String email = uniqueEmail();
        UserProvisioningResult created = restTemplate.postForEntity(
                url("/internal/users"),
                new CreateUserRequest("Password User", email, "old-hash"),
                UserProvisioningResult.class).getBody();
        createdUserId = created.userId();

        restTemplate.patchForObject(
                url("/internal/users/" + created.userId() + "/password"),
                new UpdatePasswordRequest("new-hash"), Void.class);

        UserCredentialsResult updated = restTemplate.getForObject(
                url("/internal/users/" + created.userId()), UserCredentialsResult.class);
        assertEquals("new-hash", updated.passwordHash());

        ResponseEntity<ProblemDetail> missing = restTemplate.exchange(
                url("/internal/users/999999/password"),
                org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(new UpdatePasswordRequest("whatever")),
                ProblemDetail.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uniqueEmail() {
        return "internal-user-test-" + System.nanoTime() + "@example.com";
    }
}
