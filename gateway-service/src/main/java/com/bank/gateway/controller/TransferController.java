package com.bank.gateway.controller;

import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/initiate")
    public Mono<ResponseEntity<TransferInitiateResponse>> initiate(@Valid @RequestBody TransferInitiateRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> Long.valueOf((String) context.getAuthentication().getPrincipal()))
                .flatMap(userId -> transferService.initiate(userId, request))
                .map(ResponseEntity::ok);
    }
}
