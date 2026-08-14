package com.bank.gateway.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferInitiateRequest(
        @NotNull(message = "sourceAccountId is required")
        Long sourceAccountId,

        @NotNull(message = "destinationAccountId is required")
        Long destinationAccountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        @DecimalMax(value = "5000.00", message = "amount cannot exceed 5000")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        String description
) {
}
