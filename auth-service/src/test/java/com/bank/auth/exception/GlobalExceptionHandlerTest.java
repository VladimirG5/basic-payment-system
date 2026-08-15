package com.bank.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidCredentialsMapsTo401() {
        ProblemDetail problem = handler.handleInvalidCredentials(new InvalidCredentialsException("bad login"));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Invalid Credentials", problem.getTitle());
        assertEquals("bad login", problem.getDetail());
    }

    @Test
    void coreServiceExceptionKeepsCoresOwnStatusAndTitle() {
        CoreServiceException ex = new CoreServiceException(HttpStatus.CONFLICT, "Email Already Registered", "taken");

        ProblemDetail problem = handler.handleCoreServiceException(ex);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Email Already Registered", problem.getTitle());
        assertEquals("taken", problem.getDetail());
    }

    @Test
    void validationFailureMapsTo400WithFieldMessagesJoined() throws NoSuchMethodException {
        Method registerMethod = DummyTarget.class.getMethod("register", String.class);
        MethodParameter methodParameter = new MethodParameter(registerMethod, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "email must be valid"));
        WebExchangeBindException ex = new WebExchangeBindException(methodParameter, bindingResult);

        ProblemDetail problem = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Validation Failed", problem.getTitle());
        assertTrue(problem.getDetail().contains("email: email must be valid"));
    }

    private static final class DummyTarget {
        public void register(String email) {
        }
    }
}
