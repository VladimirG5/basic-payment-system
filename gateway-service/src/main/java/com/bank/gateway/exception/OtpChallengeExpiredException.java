package com.bank.gateway.exception;

public class OtpChallengeExpiredException extends RuntimeException {

    public OtpChallengeExpiredException(String message) {
        super(message);
    }
}
