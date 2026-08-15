package com.bank.gateway.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.gateway.audit.AuditLogger;
import com.bank.gateway.dto.TransferConfirmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies AuditLogger actually gets invoked (with well-formed content) on the
 * security-sensitive too-many-OTP-attempts path, using a real AuditLogger against the "AUDIT"
 * logger captured via a Logback ListAppender - mirrors auth-service's
 * AuthServiceAuditLoggingTest.
 */
class TransferConfirmServiceAuditLoggingTest {

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
    void tooManyOtpAttemptsWritesAnAuditEventWithThatOutcome() {
        OtpChallengeService otpChallengeService = mock(OtpChallengeService.class);
        PaymentCoreClient paymentCoreClient = mock(PaymentCoreClient.class);
        TransferIdempotencyService idempotencyService = mock(TransferIdempotencyService.class);
        // findAndRegisterModules() picks up jackson-datatype-jsr310 (for Instant), the same as
        // Spring Boot's auto-configured ObjectMapper bean does in the real app - a bare
        // `new ObjectMapper()` can't serialize AuditEvent's Instant field and silently falls
        // back to AuditLogger's warn-and-swallow path instead of throwing.
        AuditLogger auditLogger = new AuditLogger(new ObjectMapper().findAndRegisterModules());
        TransferConfirmService confirmService = new TransferConfirmService(
                otpChallengeService, paymentCoreClient, idempotencyService, auditLogger);

        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "000000"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.TOO_MANY_ATTEMPTS, null));

        StepVerifier.create(confirmService.confirm(42L, "idem-key", new TransferConfirmRequest("challenge-1", "000000")))
                .expectError()
                .verify();

        assertEquals(1, auditAppender.list.size());
        String message = auditAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("\"action\":\"OTP_VALIDATE\""));
        assertTrue(message.contains("\"outcome\":\"TOO_MANY_ATTEMPTS\""));
        assertTrue(message.contains("\"actor\":\"42\""));
    }
}
