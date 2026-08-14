package com.bank.gateway.controller;

import com.bank.gateway.dto.AccountResponse;
import com.bank.gateway.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}")
    public Mono<ResponseEntity<AccountResponse>> getAccount(@PathVariable Long accountId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> Long.valueOf((String) context.getAuthentication().getPrincipal()))
                .flatMap(userId -> accountService.getAccount(userId, accountId))
                .map(ResponseEntity::ok);
    }
}
