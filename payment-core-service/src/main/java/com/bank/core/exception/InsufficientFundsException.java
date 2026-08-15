package com.bank.core.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("Insufficient funds: you have " + availableBalance.setScale(2, RoundingMode.HALF_UP)
                + " available but requested " + requestedAmount.setScale(2, RoundingMode.HALF_UP));
    }
}
