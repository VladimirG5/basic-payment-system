package com.bank.auth.dto;

/**
 * Mirrors payment-core-service's com.bank.core.dto.UserCredentialsResult - the response shape
 * from the by-email/by-id lookup endpoints. Never leaves auth-service; login/change-password
 * consume the passwordHash locally and it's never echoed back to a client.
 */
public record UserCredentials(Long userId, String fullName, String email, String passwordHash) {
}
