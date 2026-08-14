package com.bank.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mirrors payment-core-service's com.bank.core.service.TransferResult field-for-field, to
 * deserialize the response from POST /internal/transfers.
 */
public record InternalTransferResult(
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
