package com.bank.gateway.dto;

import java.math.BigDecimal;

/**
 * Mirrors payment-core-service's com.bank.core.dto.InternalTransferRequest field-for-field -
 * the JSON contract between the two services, not a shared library on purpose (each service
 * owns its own DTOs, per the project's no-shared-module approach).
 */
public record InternalTransferRequest(
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String currency,
        String referenceNote
) {
}
