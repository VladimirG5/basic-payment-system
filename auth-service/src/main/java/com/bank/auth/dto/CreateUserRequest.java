package com.bank.auth.dto;

/**
 * Mirrors payment-core-service's com.bank.core.dto.CreateUserRequest field-for-field - the
 * outgoing payload to POST /internal/users. Carries the already-hashed password; auth-service
 * is the only service that ever sees the plaintext one.
 */
public record CreateUserRequest(String fullName, String email, String passwordHash) {
}
