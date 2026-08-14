package com.bank.core.service;

import com.bank.core.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The single most important test in the system: proves the deterministic lock-ordering in
 * TransferService.executeTransfer actually prevents deadlock under real opposite-direction
 * concurrent load, against a real MySQL instance (no mocking - see application.yml/docker-compose
 * for connection details, DB_HOST/DB_PORT overridable via env for CI).
 */
@SpringBootTest
class TransferServiceConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void oppositeDirectionTransfersBetweenSameTwoAccountsDoNotDeadlockAndSettleCorrectly() throws Exception {
        BigDecimal amount = new BigDecimal("25.0000");
        BigDecimal account1Before = accountRepository.findById(1L).orElseThrow().getBalance();
        BigDecimal account2Before = accountRepository.findById(2L).orElseThrow().getBalance();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<TransferResult> threadA = () -> {
            bothReady.countDown();
            go.await();
            return transferService.executeTransfer(1L, 2L, amount, "USD", "concurrency test A->B");
        };
        Callable<TransferResult> threadB = () -> {
            bothReady.countDown();
            go.await();
            return transferService.executeTransfer(2L, 1L, amount, "USD", "concurrency test B->A");
        };

        try {
            Future<TransferResult> futureA = executor.submit(threadA);
            Future<TransferResult> futureB = executor.submit(threadB);

            bothReady.await();
            go.countDown();

            // A bounded get() timeout turns a real deadlock into a clear test failure
            // instead of hanging the build forever.
            TransferResult resultA = futureA.get(15, TimeUnit.SECONDS);
            TransferResult resultB = futureB.get(15, TimeUnit.SECONDS);

            assertEquals("COMPLETED", resultA.status());
            assertEquals("COMPLETED", resultB.status());
        } finally {
            executor.shutdownNow();
        }

        // Equal amounts moved in opposite directions cancel out exactly.
        BigDecimal account1After = accountRepository.findById(1L).orElseThrow().getBalance();
        BigDecimal account2After = accountRepository.findById(2L).orElseThrow().getBalance();

        assertEquals(account1Before, account1After);
        assertEquals(account2Before, account2After);
    }
}
