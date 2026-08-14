package com.bank.gateway.dto;

import java.time.Instant;

public record TransferInitiateResponse(
        String challengeId,
        Instant expiresAt,
        String status
) {
}
