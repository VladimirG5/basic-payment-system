package com.bank.gateway.service;

import com.bank.gateway.dto.OtpChallengeDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory OTP challenges for transfer SCA, backed by Caffeine rather than Redis - this is a
 * single-instance demo, and a shared cache isn't worth the operational cost on a 3-day task.
 * The cache stores only the bcrypt-hashed OTP; the raw code exists transiently in
 * CreatedOtpChallenge, between create() and send(), and is never persisted anywhere.
 */
@Service
public class OtpChallengeService {

    private static final Logger log = LoggerFactory.getLogger(OtpChallengeService.class);

    private static final Duration TTL = Duration.ofMinutes(3);
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cache<String, OtpChallengeDto> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(10_000)
            .build();

    private final PasswordEncoder passwordEncoder;

    public OtpChallengeService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public CreatedOtpChallenge create(Long userId, Long sourceAccountId, Long destinationAccountId,
                                       BigDecimal amount, String currency, String description) {
        String challengeId = UUID.randomUUID().toString();
        String rawOtpCode = generateOtpCode();
        Instant expiresAt = Instant.now().plus(TTL);

        OtpChallengeDto dto = new OtpChallengeDto(challengeId, passwordEncoder.encode(rawOtpCode), userId,
                sourceAccountId, destinationAccountId, amount, currency, description, expiresAt, 0);
        cache.put(challengeId, dto);

        return new CreatedOtpChallenge(challengeId, rawOtpCode, expiresAt);
    }

    /**
     * Mock delivery: logs the code to the console (there's no real SMS/email provider, and no
     * phone number in the schema) and returns a masked hint a real API response could show the
     * user, e.g. "OTP sent to +XXXXXX1234".
     */
    public String send(CreatedOtpChallenge challenge) {
        String maskedHint = maskedPhoneHint(challenge.challengeId());
        log.info("[MOCK OTP DELIVERY] challengeId={} code={} -> {}",
                challenge.challengeId(), challenge.rawOtpCode(), maskedHint);
        return "OTP sent to " + maskedHint;
    }

    public OtpValidationResult validate(String challengeId, String otpCode) {
        AtomicReference<OtpValidationResult> outcome = new AtomicReference<>();

        cache.asMap().compute(challengeId, (id, existing) -> {
            if (existing == null) {
                outcome.set(OtpValidationResult.EXPIRED);
                return null;
            }
            if (existing.attemptCount() >= MAX_ATTEMPTS) {
                outcome.set(OtpValidationResult.TOO_MANY_ATTEMPTS);
                return existing;
            }
            if (passwordEncoder.matches(otpCode, existing.hashedOtpCode())) {
                outcome.set(OtpValidationResult.SUCCESS);
                return null; // one-time use
            }
            outcome.set(OtpValidationResult.FAILURE);
            return existing.withIncrementedAttempts();
        });

        return outcome.get();
    }

    private String generateOtpCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String maskedPhoneHint(String challengeId) {
        String digits = challengeId.replaceAll("\\D", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : "0000";
        return "+XXXXXX" + last4;
    }

    public record CreatedOtpChallenge(String challengeId, String rawOtpCode, Instant expiresAt) {
    }
}
