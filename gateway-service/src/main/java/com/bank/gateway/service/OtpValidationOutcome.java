package com.bank.gateway.service;

import com.bank.gateway.dto.OtpChallengeDto;

/**
 * challenge is populated only when result is SUCCESS - it carries the transfer context that
 * was gated behind the OTP, needed by the caller to actually execute the transfer. The
 * challenge is already removed from the cache (one-time use) by the time this is returned.
 */
public record OtpValidationOutcome(OtpValidationResult result, OtpChallengeDto challenge) {
}
