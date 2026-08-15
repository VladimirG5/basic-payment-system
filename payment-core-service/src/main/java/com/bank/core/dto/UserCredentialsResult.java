package com.bank.core.dto;

public record UserCredentialsResult(
        Long userId,
        String fullName,
        String email,
        String passwordHash
) {
}
