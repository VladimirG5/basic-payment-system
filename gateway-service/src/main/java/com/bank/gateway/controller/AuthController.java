package com.bank.gateway.controller;

import com.bank.gateway.dto.ChangePasswordRequest;
import com.bank.gateway.dto.LoginRequest;
import com.bank.gateway.dto.LoginResponse;
import com.bank.gateway.dto.RegisterRequest;
import com.bank.gateway.dto.RegisterResponse;
import com.bank.gateway.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request).map(ResponseEntity::ok);
    }

    @PostMapping("/change-password")
    public Mono<ResponseEntity<Void>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(authorizationHeader, request)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
