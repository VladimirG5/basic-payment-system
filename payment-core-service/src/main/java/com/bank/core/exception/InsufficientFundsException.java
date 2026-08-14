package com.bank.core.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("Account " + accountId + " has insufficient funds: requested=" + requestedAmount
                + ", available=" + availableBalance);
    }
}
