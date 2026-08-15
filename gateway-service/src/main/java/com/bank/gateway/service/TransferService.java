package com.bank.gateway.service;

import com.bank.gateway.audit.AuditLogger;
import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive wrapper around TransferInitiationExecutor's blocking JDBC calls, same pattern as
 * AccountService: offload to boundedElastic so Netty's event-loop threads are never blocked.
 * No explicit MDC context-write is needed here for the audit actor - UserIdMdcWebFilter already
 * populates "userId" for every authenticated request, this route included.
 */
@Service
public class TransferService {

    private final TransferInitiationExecutor transferInitiationExecutor;
    private final AuditLogger auditLogger;

    public TransferService(TransferInitiationExecutor transferInitiationExecutor, AuditLogger auditLogger) {
        this.transferInitiationExecutor = transferInitiationExecutor;
        this.auditLogger = auditLogger;
    }

    public Mono<TransferInitiateResponse> initiate(Long userId, TransferInitiateRequest request) {
        return Mono.fromCallable(() -> transferInitiationExecutor.execute(userId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(response -> auditLogger.log(String.valueOf(userId), "TRANSFER_INITIATE", "SUCCESS",
                        "sourceAccount:" + request.sourceAccountId(), "challengeId=" + response.challengeId()))
                .doOnError(ex -> auditLogger.log(String.valueOf(userId), "TRANSFER_INITIATE", "FAILURE",
                        "sourceAccount:" + request.sourceAccountId(), ex.getMessage()));
    }
}
