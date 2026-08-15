package com.bank.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Carries payment-core-service's own RFC 7807 status/title/detail back through auth-service
 * unchanged (e.g. the 409 from a duplicate email on register) - mirrors gateway-service's
 * identically-named class and its PaymentCoreClient/CoreServiceException pattern exactly.
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
