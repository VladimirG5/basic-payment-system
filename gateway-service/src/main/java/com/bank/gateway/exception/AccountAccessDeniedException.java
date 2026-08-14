package com.bank.gateway.exception;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(Long accountId) {
        super("Account does not belong to the authenticated user: id=" + accountId);
    }
}
