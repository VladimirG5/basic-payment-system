package com.bank.gateway.service;

import com.bank.gateway.audit.AuditLogger;
import com.bank.gateway.dto.InternalTransferResult;
import com.bank.gateway.dto.OtpChallengeDto;
import com.bank.gateway.dto.TransferConfirmRequest;
import com.bank.gateway.dto.TransferConfirmResponse;
import com.bank.gateway.exception.InvalidOtpException;
import com.bank.gateway.exception.OtpChallengeExpiredException;
import com.bank.gateway.exception.OtpChallengeOwnershipException;
import com.bank.gateway.exception.TooManyOtpAttemptsException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Complements TransferConfirmServiceAuditLoggingTest (which only exercises the too-many-attempts
 * audit path) with the rest of confirm()'s branches: idempotent replay, each OtpValidationResult
 * outcome, ownership mismatch, and the payment-core-service call itself.
 */
class TransferConfirmServiceTest {

    private final OtpChallengeService otpChallengeService = mock(OtpChallengeService.class);
    private final PaymentCoreClient paymentCoreClient = mock(PaymentCoreClient.class);
    private final TransferIdempotencyService idempotencyService = mock(TransferIdempotencyService.class);
    private final AuditLogger auditLogger = mock(AuditLogger.class);
    private final TransferConfirmService confirmService =
            new TransferConfirmService(otpChallengeService, paymentCoreClient, idempotencyService, auditLogger);

    private static OtpChallengeDto challengeFor(long userId) {
        return new OtpChallengeDto("challenge-1", "hashed", userId, 10L, 20L,
                new BigDecimal("75.00"), "USD", "rent", Instant.now().plusSeconds(180), 0);
    }

    @Test
    void confirmReturnsTheCachedResponseWithoutRevalidatingTheOtp() {
        TransferConfirmResponse cached = new TransferConfirmResponse("SUCCESS", 4L, new BigDecimal("925.00"));
        when(idempotencyService.get("idem-key")).thenReturn(cached);

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .expectNext(cached)
                .verifyComplete();

        verify(otpChallengeService, never()).validate(any(), any());
    }

    @Test
    void confirmFailsWithInvalidOtpOnAWrongCode() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "000000"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.FAILURE, null));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "000000")))
                .expectError(InvalidOtpException.class)
                .verify();
    }

    @Test
    void confirmFailsWithExpiredWhenTheChallengeIsGone() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "123456"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.EXPIRED, null));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .expectError(OtpChallengeExpiredException.class)
                .verify();
    }

    @Test
    void confirmFailsWithTooManyAttempts() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "123456"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.TOO_MANY_ATTEMPTS, null));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .expectError(TooManyOtpAttemptsException.class)
                .verify();
    }

    @Test
    void confirmRejectsAChallengeThatBelongsToAnotherUser() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "123456"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.SUCCESS, challengeFor(2L)));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .expectError(OtpChallengeOwnershipException.class)
                .verify();

        verify(paymentCoreClient, never()).executeTransfer(any(), any());
    }

    @Test
    void confirmExecutesTheTransferAndCachesTheResponseOnSuccess() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "123456"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.SUCCESS, challengeFor(1L)));

        InternalTransferResult result = new InternalTransferResult(
                4L, 10L, 20L, new BigDecimal("925.00"), new BigDecimal("75.00"),
                new BigDecimal("75.00"), "USD", "SUCCESS", Instant.now());
        when(paymentCoreClient.executeTransfer(any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(Mono.just(result));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .assertNext(response -> {
                    assertEquals("SUCCESS", response.status());
                    assertEquals(4L, response.transactionId());
                    assertEquals(new BigDecimal("925.00"), response.newBalance());
                })
                .verifyComplete();

        verify(idempotencyService).put(org.mockito.ArgumentMatchers.eq("idem-key"), any());
    }

    @Test
    void confirmPropagatesAFailureFromPaymentCoreService() {
        when(idempotencyService.get("idem-key")).thenReturn(null);
        when(otpChallengeService.validate("challenge-1", "123456"))
                .thenReturn(new OtpValidationOutcome(OtpValidationResult.SUCCESS, challengeFor(1L)));
        when(paymentCoreClient.executeTransfer(any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(Mono.error(new RuntimeException("core unavailable")));

        StepVerifier.create(confirmService.confirm(1L, "idem-key", new TransferConfirmRequest("challenge-1", "123456")))
                .expectErrorMessage("core unavailable")
                .verify();

        verify(idempotencyService, never()).put(any(), any());
    }
}
