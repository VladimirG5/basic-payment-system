package com.bank.core.service;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResult(
        Long transactionId,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal sourceNewBalance,
        BigDecimal destinationNewBalance,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {
}
