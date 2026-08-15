package com.bank.core.dto;

import java.math.BigDecimal;

public record UserProvisioningResult(
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
