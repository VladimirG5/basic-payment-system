package com.bank.gateway.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long accountId,
        String iban,
        BigDecimal balance,
        String currency,
        String status
) {
}
