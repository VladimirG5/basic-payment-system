package com.bank.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long transactionId,
        String type,
        BigDecimal amount,
        String counterpartyName,
        String counterpartyAccount,
        Instant timestamp,
        String status,
        String referenceNote
) {
}
