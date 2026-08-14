package com.bank.gateway.dto;

import java.math.BigDecimal;

public record TransferConfirmResponse(
        String status,
        Long transactionId,
        BigDecimal newBalance
) {
}
