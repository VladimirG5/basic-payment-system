package com.bank.gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpChallengeServiceTest {

    private final OtpChallengeService service = new OtpChallengeService(new BCryptPasswordEncoder());

    @Test
    void createGeneratesASixDigitCodeThatValidatesSuccessfully() {
        OtpChallengeService.CreatedOtpChallenge challenge =
                service.create(1L, 10L, 20L, new BigDecimal("75.00"), "USD", "test");

        assertEquals(6, challenge.rawOtpCode().length());
        assertNotNull(challenge.expiresAt());

        OtpValidationOutcome outcome = service.validate(challenge.challengeId(), challenge.rawOtpCode());
        assertEquals(OtpValidationResult.SUCCESS, outcome.result());
        assertEquals(10L, outcome.challenge().sourceAccountId());
    }

    @Test
    void validateReturnsExpiredForAnUnknownChallengeId() {
        OtpValidationOutcome outcome = service.validate("does-not-exist", "000000");
        assertEquals(OtpValidationResult.EXPIRED, outcome.result());
    }

    @Test
    void validateReturnsFailureForAWrongCodeAndAllowsARetry() {
        OtpChallengeService.CreatedOtpChallenge challenge =
                service.create(1L, 10L, 20L, new BigDecimal("75.00"), "USD", "test");

        OtpValidationOutcome first = service.validate(challenge.challengeId(), "000000");
        assertEquals(OtpValidationResult.FAILURE, first.result());

        // the challenge should still be usable after a single wrong attempt
        OtpValidationOutcome retry = service.validate(challenge.challengeId(), challenge.rawOtpCode());
        assertEquals(OtpValidationResult.SUCCESS, retry.result());
    }

    @Test
    void validateLocksOutAfterTooManyWrongAttempts() {
        OtpChallengeService.CreatedOtpChallenge challenge =
                service.create(1L, 10L, 20L, new BigDecimal("75.00"), "USD", "test");

        service.validate(challenge.challengeId(), "000000");
        service.validate(challenge.challengeId(), "000000");
        service.validate(challenge.challengeId(), "000000");

        OtpValidationOutcome outcome = service.validate(challenge.challengeId(), challenge.rawOtpCode());
        assertEquals(OtpValidationResult.TOO_MANY_ATTEMPTS, outcome.result());
    }

    @Test
    void validateIsOneTimeUse() {
        OtpChallengeService.CreatedOtpChallenge challenge =
                service.create(1L, 10L, 20L, new BigDecimal("75.00"), "USD", "test");

        assertEquals(OtpValidationResult.SUCCESS, service.validate(challenge.challengeId(), challenge.rawOtpCode()).result());
        assertEquals(OtpValidationResult.EXPIRED, service.validate(challenge.challengeId(), challenge.rawOtpCode()).result());
    }

    @Test
    void sendReturnsAMaskedHintDerivedFromTheChallengeId() {
        OtpChallengeService.CreatedOtpChallenge challenge =
                service.create(1L, 10L, 20L, new BigDecimal("75.00"), "USD", "test");

        String hint = service.send(challenge);
        assertTrue(hint.startsWith("OTP sent to +XXXXXX"));
    }
}
