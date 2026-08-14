package com.bank.gateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Carries payment-core-service's own RFC 7807 status/title/detail back through gateway
 * unchanged, so a client confirming a transfer sees the real reason (e.g. insufficient funds
 * discovered again under lock, or the destination account vanishing between initiate and
 * confirm) rather than a generic proxy failure.
 */
public class CoreServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public CoreServiceException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
