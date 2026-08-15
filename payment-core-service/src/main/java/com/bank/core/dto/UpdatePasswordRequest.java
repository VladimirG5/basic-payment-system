package com.bank.core.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePasswordRequest(
        @NotBlank(message = "passwordHash is required")
        String passwordHash
) {
}
