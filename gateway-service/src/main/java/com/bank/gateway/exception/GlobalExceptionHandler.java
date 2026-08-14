package com.bank.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.stream.Collectors;

/**
 * Maps auth-domain and validation exceptions to RFC 7807 problem-detail responses, mirroring
 * payment-core-service's GlobalExceptionHandler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email Already Registered");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid Credentials");
        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Account Not Found");
        return problem;
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ProblemDetail handleAccountAccessDenied(AccountAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Insufficient Funds");
        return problem;
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ProblemDetail handleInvalidTransfer(InvalidTransferException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Transfer");
        return problem;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        String detail = ex.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return problem;
    }
}
