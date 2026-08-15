package com.bank.auth.dto;

import java.math.BigDecimal;

public record RegisterResponse(
        Long userId,
        String fullName,
        String email,
        Long accountId,
        String iban,
        BigDecimal balance,
        String currency,
        String accountStatus
) {
}
