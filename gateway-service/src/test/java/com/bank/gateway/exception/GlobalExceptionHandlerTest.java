package com.bank.gateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidCredentialsMapsTo401() {
        ProblemDetail problem = handler.handleInvalidCredentials(new InvalidCredentialsException("bad creds"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Invalid Credentials", problem.getTitle());
        assertEquals("bad creds", problem.getDetail());
    }

    @Test
    void accountNotFoundMapsTo404() {
        ProblemDetail problem = handler.handleAccountNotFound(new AccountNotFoundException(5L));
        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Account Not Found", problem.getTitle());
    }

    @Test
    void accountAccessDeniedMapsTo403() {
        ProblemDetail problem = handler.handleAccountAccessDenied(new AccountAccessDeniedException(5L));
        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Forbidden", problem.getTitle());
    }

    @Test
    void insufficientFundsMapsTo422() {
        ProblemDetail problem = handler.handleInsufficientFunds(
                new InsufficientFundsException(5L, new BigDecimal("100"), new BigDecimal("10")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
        assertEquals("Insufficient Funds", problem.getTitle());
    }

    @Test
    void invalidTransferMapsTo400() {
        ProblemDetail problem = handler.handleInvalidTransfer(new InvalidTransferException("same account"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Invalid Transfer", problem.getTitle());
    }

    @Test
    void invalidOtpMapsTo400() {
        ProblemDetail problem = handler.handleInvalidOtp(new InvalidOtpException("wrong code"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Invalid OTP", problem.getTitle());
    }

    @Test
    void otpChallengeExpiredMapsTo400() {
        ProblemDetail problem = handler.handleOtpChallengeExpired(new OtpChallengeExpiredException("expired"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("OTP Challenge Expired", problem.getTitle());
    }

    @Test
    void tooManyOtpAttemptsMapsTo429() {
        ProblemDetail problem = handler.handleTooManyOtpAttempts(new TooManyOtpAttemptsException("too many"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), problem.getStatus());
        assertEquals("Too Many OTP Attempts", problem.getTitle());
    }

    @Test
    void otpChallengeOwnershipMapsTo403() {
        ProblemDetail problem = handler.handleOtpChallengeOwnership(new OtpChallengeOwnershipException("not yours"));
        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Forbidden", problem.getTitle());
    }

    @Test
    void coreServiceExceptionPassesThroughTheOriginalStatusAndTitle() {
        ProblemDetail problem = handler.handleCoreServiceException(
                new CoreServiceException(HttpStatus.CONFLICT, "Duplicate Email", "already registered"));
        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Duplicate Email", problem.getTitle());
        assertEquals("already registered", problem.getDetail());
    }
}
