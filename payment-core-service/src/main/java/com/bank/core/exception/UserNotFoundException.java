package com.bank.core.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("User not found: id=" + userId);
    }

    public static UserNotFoundException forEmail(String email) {
        return new UserNotFoundException("User not found: email=" + email);
    }

    private UserNotFoundException(String message) {
        super(message);
    }
}
