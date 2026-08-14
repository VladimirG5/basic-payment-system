package com.bank.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The value stored in OtpChallengeService's Caffeine cache: the hashed OTP plus the transfer
 * context it gates. Never carries the raw OTP code - that only ever exists transiently in
 * OtpChallengeService.CreatedOtpChallenge, between create() and send().
 */
public record OtpChallengeDto(
        String challengeId,
        String hashedOtpCode,
        Long userId,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String currency,
        String description,
        Instant expiresAt,
        int attemptCount
) {
    public OtpChallengeDto withIncrementedAttempts() {
        return new OtpChallengeDto(challengeId, hashedOtpCode, userId, sourceAccountId,
                destinationAccountId, amount, currency, description, expiresAt, attemptCount + 1);
    }
}
