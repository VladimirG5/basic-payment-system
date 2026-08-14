package com.bank.gateway.service;

import com.bank.gateway.dto.TransferConfirmResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caches successful transfer-confirm responses by X-Idempotency-Key so a retried request (e.g.
 * client timeout on the way back after the transfer already succeeded) returns the original
 * result instead of attempting to re-consume an already-spent OTP challenge. Only success is
 * cached: OTP failures are inherently safe to re-evaluate on retry (nothing was executed), and
 * caching them would need re-plumbing every exception path through here. Known gap: two
 * requests with the same brand-new key arriving concurrently can both proceed - acceptable for
 * a single-instance demo cache, called out in the README trade-offs.
 */
@Service
public class TransferIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final Cache<String, TransferConfirmResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(50_000)
            .build();

    public TransferConfirmResponse get(String idempotencyKey) {
        return cache.getIfPresent(idempotencyKey);
    }

    public void put(String idempotencyKey, TransferConfirmResponse response) {
        cache.put(idempotencyKey, response);
    }
}
