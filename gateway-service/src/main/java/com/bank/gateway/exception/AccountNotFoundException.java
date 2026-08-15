package com.bank.gateway.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long accountId) {
        super("Account not found: id=" + accountId);
    }

    public static AccountNotFoundException forUser(Long userId) {
        return new AccountNotFoundException("No account found for userId=" + userId);
    }

    private AccountNotFoundException(String message) {
        super(message);
    }
}
