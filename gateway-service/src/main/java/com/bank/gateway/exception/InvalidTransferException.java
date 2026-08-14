package com.bank.gateway.exception;

public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
