package com.bank.gateway.service;

import com.bank.gateway.audit.AuditLogger;
import com.bank.gateway.dto.InternalTransferRequest;
import com.bank.gateway.dto.OtpChallengeDto;
import com.bank.gateway.dto.TransferConfirmRequest;
import com.bank.gateway.dto.TransferConfirmResponse;
import com.bank.gateway.exception.InvalidOtpException;
import com.bank.gateway.exception.OtpChallengeExpiredException;
import com.bank.gateway.exception.OtpChallengeOwnershipException;
import com.bank.gateway.exception.TooManyOtpAttemptsException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TransferConfirmService {

    private final OtpChallengeService otpChallengeService;
    private final PaymentCoreClient paymentCoreClient;
    private final TransferIdempotencyService idempotencyService;
    private final AuditLogger auditLogger;

    public TransferConfirmService(OtpChallengeService otpChallengeService, PaymentCoreClient paymentCoreClient,
                                   TransferIdempotencyService idempotencyService, AuditLogger auditLogger) {
        this.otpChallengeService = otpChallengeService;
        this.paymentCoreClient = paymentCoreClient;
        this.idempotencyService = idempotencyService;
        this.auditLogger = auditLogger;
    }

    public Mono<TransferConfirmResponse> confirm(Long userId, String idempotencyKey, TransferConfirmRequest request) {
        TransferConfirmResponse cached = idempotencyService.get(idempotencyKey);
        if (cached != null) {
            return Mono.just(cached);
        }

        OtpValidationOutcome outcome = otpChallengeService.validate(request.challengeId(), request.otpCode());
        auditLogger.log(String.valueOf(userId), "OTP_VALIDATE", outcome.result().name(),
                "challenge:" + request.challengeId(), null);

        return switch (outcome.result()) {
            case SUCCESS -> executeTransfer(userId, idempotencyKey, outcome.challenge());
            case FAILURE -> Mono.error(new InvalidOtpException("Invalid OTP code"));
            case EXPIRED -> Mono.error(new OtpChallengeExpiredException("OTP challenge not found or expired"));
            case TOO_MANY_ATTEMPTS -> Mono.error(new TooManyOtpAttemptsException("Too many invalid OTP attempts"));
        };
    }

    private Mono<TransferConfirmResponse> executeTransfer(Long userId, String idempotencyKey, OtpChallengeDto challenge) {
        if (!challenge.userId().equals(userId)) {
            auditLogger.log(String.valueOf(userId), "TRANSFER_CONFIRM", "FAILURE",
                    "challenge:" + challenge.challengeId(), "OTP challenge ownership mismatch");
            return Mono.error(new OtpChallengeOwnershipException(
                    "OTP challenge does not belong to the authenticated user"));
        }

        InternalTransferRequest coreRequest = new InternalTransferRequest(
                challenge.sourceAccountId(), challenge.destinationAccountId(),
                challenge.amount(), challenge.currency(), challenge.description());

        return paymentCoreClient.executeTransfer(coreRequest, userId)
                .map(result -> new TransferConfirmResponse("SUCCESS", result.transactionId(), result.sourceNewBalance()))
                .doOnNext(response -> {
                    idempotencyService.put(idempotencyKey, response);
                    auditLogger.log(String.valueOf(userId), "TRANSFER_CONFIRM", "SUCCESS",
                            "transactionId:" + response.transactionId(), null);
                })
                .doOnError(ex -> auditLogger.log(String.valueOf(userId), "TRANSFER_CONFIRM", "FAILURE",
                        "challenge:" + challenge.challengeId(), ex.getMessage()));
    }
}
