package com.bank.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.auth.audit.AuditLogger;
import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.UserCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies AuditLogger actually gets invoked (with well-formed content) on the
 * security-sensitive login-failure path, using a real AuditLogger against the "AUDIT" logger
 * captured via a Logback ListAppender - the standard SLF4J/Logback testing pattern. Everything
 * else in AuthServiceTest mocks AuditLogger away; this is the one place that exercises it for
 * real.
 */
class AuthServiceAuditLoggingTest {

    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void attachAuditAppender() {
        auditAppender = new ListAppender<>();
        auditAppender.start();
        ((Logger) LoggerFactory.getLogger("AUDIT")).addAppender(auditAppender);
    }

    @AfterEach
    void detachAuditAppender() {
        ((Logger) LoggerFactory.getLogger("AUDIT")).detachAppender(auditAppender);
    }

    @Test
    void loginWithAWrongPasswordWritesAFailureAuditEvent() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        CoreUserClient coreUserClient = mock(CoreUserClient.class);
        // findAndRegisterModules() picks up jackson-datatype-jsr310 (for Instant), the same as
        // Spring Boot's auto-configured ObjectMapper bean does in the real app - a bare
        // `new ObjectMapper()` can't serialize AuditEvent's Instant field and silently falls
        // back to AuditLogger's warn-and-swallow path instead of throwing.
        AuditLogger auditLogger = new AuditLogger(new ObjectMapper().findAndRegisterModules());
        AuthService authService = new AuthService(passwordEncoder, jwtService, coreUserClient, auditLogger);

        UserCredentials user = new UserCredentials(1L, "Ada Lovelace", "ada@example.com", "hashed");
        when(coreUserClient.findByEmail("ada@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        StepVerifier.create(authService.login(new LoginRequest("ada@example.com", "wrong")))
                .expectError()
                .verify();

        assertEquals(1, auditAppender.list.size());
        String message = auditAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("\"action\":\"LOGIN\""));
        assertTrue(message.contains("\"outcome\":\"FAILURE\""));
        assertTrue(message.contains("\"actor\":\"ada@example.com\""));
    }
}
