package com.bank.auth.dto;

/**
 * Mirrors payment-core-service's com.bank.core.dto.UpdatePasswordRequest - the outgoing
 * payload to PATCH /internal/users/{userId}/password.
 */
public record UpdatePasswordRequest(String passwordHash) {
}
