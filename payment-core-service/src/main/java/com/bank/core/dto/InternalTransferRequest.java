package com.bank.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InternalTransferRequest(
        @NotNull(message = "sourceAccountId is required")
        Long sourceAccountId,

        @NotNull(message = "destinationAccountId is required")
        Long destinationAccountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        String referenceNote
) {
}
