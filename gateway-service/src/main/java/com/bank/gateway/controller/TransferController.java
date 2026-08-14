package com.bank.gateway.controller;

import com.bank.gateway.dto.TransferConfirmRequest;
import com.bank.gateway.dto.TransferConfirmResponse;
import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.exception.InvalidTransferException;
import com.bank.gateway.service.TransferConfirmService;
import com.bank.gateway.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferConfirmService transferConfirmService;

    public TransferController(TransferService transferService, TransferConfirmService transferConfirmService) {
        this.transferService = transferService;
        this.transferConfirmService = transferConfirmService;
    }

    @PostMapping("/initiate")
    public Mono<ResponseEntity<TransferInitiateResponse>> initiate(@Valid @RequestBody TransferInitiateRequest request) {
        return authenticatedUserId()
                .flatMap(userId -> transferService.initiate(userId, request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/confirm")
    public Mono<ResponseEntity<TransferConfirmResponse>> confirm(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferConfirmRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new InvalidTransferException("X-Idempotency-Key header is required"));
        }
        return authenticatedUserId()
                .flatMap(userId -> transferConfirmService.confirm(userId, idempotencyKey, request))
                .map(ResponseEntity::ok);
    }

    private Mono<Long> authenticatedUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> Long.valueOf((String) context.getAuthentication().getPrincipal()));
    }
}
