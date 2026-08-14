package com.bank.gateway.service;

import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive wrapper around TransferInitiationExecutor's blocking JDBC calls, same pattern as
 * AuthService: offload to boundedElastic so Netty's event-loop threads are never blocked.
 */
@Service
public class TransferService {

    private final TransferInitiationExecutor transferInitiationExecutor;

    public TransferService(TransferInitiationExecutor transferInitiationExecutor) {
        this.transferInitiationExecutor = transferInitiationExecutor;
    }

    public Mono<TransferInitiateResponse> initiate(Long userId, TransferInitiateRequest request) {
        return Mono.fromCallable(() -> transferInitiationExecutor.execute(userId, request))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
