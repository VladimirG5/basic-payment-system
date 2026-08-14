package com.bank.core.controller;

import com.bank.core.dto.InternalTransferRequest;
import com.bank.core.service.TransferResult;
import com.bank.core.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoint for gateway-service to invoke after OTP confirmation - not exposed
 * through any public routing, relies on the two services sharing a private Docker network.
 * No auth of its own: gateway has already authenticated the end user and validated the OTP
 * challenge before this is ever called.
 */
@RestController
@RequestMapping("/internal/transfers")
public class InternalTransferController {

    private final TransferService transferService;

    public InternalTransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResult> execute(@Valid @RequestBody InternalTransferRequest request) {
        TransferResult result = transferService.executeTransfer(
                request.sourceAccountId(), request.destinationAccountId(),
                request.amount(), request.currency(), request.referenceNote());
        return ResponseEntity.ok(result);
    }
}
