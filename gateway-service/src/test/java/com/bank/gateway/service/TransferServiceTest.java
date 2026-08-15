package com.bank.gateway.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.gateway.audit.AuditLogger;
import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.exception.InvalidTransferException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses a real AuditLogger (against a captured "AUDIT" logger) rather than a mock, same approach
 * as TransferConfirmServiceAuditLoggingTest, so the doOnNext/doOnError audit wiring itself is
 * actually exercised, not just assumed.
 */
class TransferServiceTest {

    private final TransferInitiationExecutor transferInitiationExecutor = mock(TransferInitiationExecutor.class);
    private ListAppender<ILoggingEvent> auditAppender;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        auditAppender = new ListAppender<>();
        auditAppender.start();
        ((Logger) LoggerFactory.getLogger("AUDIT")).addAppender(auditAppender);
        AuditLogger auditLogger = new AuditLogger(new ObjectMapper().findAndRegisterModules());
        transferService = new TransferService(transferInitiationExecutor, auditLogger);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("AUDIT")).detachAppender(auditAppender);
    }

    @Test
    void initiateAuditsSuccessOnTheHappyPath() {
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 20L, new BigDecimal("75.00"), "USD", null);
        TransferInitiateResponse response = new TransferInitiateResponse(
                "challenge-1", Instant.now().plusSeconds(180), "OTP_REQUIRED");
        when(transferInitiationExecutor.execute(1L, request)).thenReturn(response);

        StepVerifier.create(transferService.initiate(1L, request))
                .assertNext(r -> assertEquals("challenge-1", r.challengeId()))
                .verifyComplete();

        assertEquals(1, auditAppender.list.size());
        String message = auditAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("\"action\":\"TRANSFER_INITIATE\""));
        assertTrue(message.contains("\"outcome\":\"SUCCESS\""));
    }

    @Test
    void initiateAuditsFailureWhenTheExecutorThrows() {
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 10L, new BigDecimal("75.00"), "USD", null);
        when(transferInitiationExecutor.execute(1L, request))
                .thenThrow(new InvalidTransferException("Source and destination account must be different"));

        StepVerifier.create(transferService.initiate(1L, request))
                .expectError(InvalidTransferException.class)
                .verify();

        assertEquals(1, auditAppender.list.size());
        String message = auditAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("\"action\":\"TRANSFER_INITIATE\""));
        assertTrue(message.contains("\"outcome\":\"FAILURE\""));
    }
}
