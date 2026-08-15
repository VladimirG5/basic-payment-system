package com.bank.core.service;

import com.bank.core.domain.Account;
import com.bank.core.domain.Transaction;
import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientFundsException;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final String TRANSACTION_STATUS_COMPLETED = "COMPLETED";
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResult executeTransfer(Long sourceAccountId, Long destinationAccountId,
                                           BigDecimal amount, String currency, String referenceNote) {
        // Always lock the lower account ID first, regardless of which side is source vs.
        // destination. Two opposite-direction transfers between the same pair of accounts
        // (A->B and B->A) would otherwise lock source-then-destination and deadlock against
        // each other; locking by ascending ID makes both threads request locks in the same
        // order, so one simply waits for the other instead of both waiting forever.
        Long lowerId = sourceAccountId.compareTo(destinationAccountId) <= 0 ? sourceAccountId : destinationAccountId;
        Long higherId = sourceAccountId.compareTo(destinationAccountId) <= 0 ? destinationAccountId : sourceAccountId;

        Account lowerAccount = accountRepository.findByIdForUpdate(lowerId)
                .orElseThrow(() -> new AccountNotFoundException(lowerId));
        Account higherAccount = accountRepository.findByIdForUpdate(higherId)
                .orElseThrow(() -> new AccountNotFoundException(higherId));

        Account sourceAccount = sourceAccountId.equals(lowerId) ? lowerAccount : higherAccount;
        Account destinationAccount = destinationAccountId.equals(lowerId) ? lowerAccount : higherAccount;

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(sourceAccountId, amount, sourceAccount.getBalance());
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        Transaction transaction = new Transaction(sourceAccountId, destinationAccountId, amount, currency,
                TRANSACTION_STATUS_COMPLETED, referenceNote);
        transaction = transactionRepository.save(transaction);

        log.info("Transfer executed: transactionId={} source={} destination={} amount={} {}",
                transaction.getId(), sourceAccountId, destinationAccountId, amount, currency);

        return new TransferResult(
                transaction.getId(),
                sourceAccountId,
                destinationAccountId,
                sourceAccount.getBalance(),
                destinationAccount.getBalance(),
                amount,
                currency,
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
