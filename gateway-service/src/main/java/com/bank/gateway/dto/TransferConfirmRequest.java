package com.bank.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferConfirmRequest(
        @NotBlank(message = "challengeId is required")
        String challengeId,

        @NotBlank(message = "otpCode is required")
        String otpCode
) {
}
