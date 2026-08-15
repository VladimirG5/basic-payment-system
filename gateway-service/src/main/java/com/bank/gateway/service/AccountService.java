package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.domain.Transaction;
import com.bank.gateway.domain.User;
import com.bank.gateway.dto.AccountResponse;
import com.bank.gateway.dto.TransactionResponse;
import com.bank.gateway.exception.AccountAccessDeniedException;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.repository.AccountRepository;
import com.bank.gateway.repository.TransactionRepository;
import com.bank.gateway.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public Mono<AccountResponse> getAccount(Long userId, Long accountId) {
        return Mono.fromCallable(() -> getAccountBlocking(userId, accountId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AccountResponse> getMyAccount(Long userId) {
        return Mono.fromCallable(() -> toResponse(findOwnedAccount(userId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<TransactionResponse>> getTransactions(Long userId, Long accountId) {
        return Mono.fromCallable(() -> getTransactionsBlocking(userId, accountId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AccountResponse getAccountBlocking(Long userId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(accountId);
        }

        return toResponse(account);
    }

    private Account findOwnedAccount(Long userId) {
        return accountRepository.findByUser_Id(userId)
                .orElseThrow(() -> AccountNotFoundException.forUser(userId));
    }

    private List<TransactionResponse> getTransactionsBlocking(Long userId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(accountId);
        }

        List<Transaction> transactions = transactionRepository
                .findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId);

        List<Long> counterpartyAccountIds = transactions.stream()
                .map(transaction -> counterpartyAccountId(transaction, accountId))
                .distinct()
                .toList();

        // Accessing a lazy @ManyToOne proxy's own id (getUser().getId()) never hits the DB -
        // Hibernate populates it from the owning row's FK column - so this stays a single
        // batched query per side instead of N+1 across transactions/accounts/users.
        Map<Long, Account> counterpartyAccountsById = accountRepository.findAllById(counterpartyAccountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<Long> counterpartyUserIds = counterpartyAccountsById.values().stream()
                .map(counterpartyAccount -> counterpartyAccount.getUser().getId())
                .distinct()
                .toList();

        Map<Long, String> counterpartyNamesByUserId = userRepository.findAllById(counterpartyUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return transactions.stream()
                .map(transaction -> toTransactionResponse(transaction, accountId, counterpartyAccountsById, counterpartyNamesByUserId))
                .toList();
    }

    private TransactionResponse toTransactionResponse(Transaction transaction, Long accountId,
                                                        Map<Long, Account> counterpartyAccountsById,
                                                        Map<Long, String> counterpartyNamesByUserId) {
        boolean sent = transaction.getSourceAccountId().equals(accountId);
        Long counterpartyAccountId = counterpartyAccountId(transaction, accountId);
        Account counterpartyAccount = counterpartyAccountsById.get(counterpartyAccountId);
        String counterpartyIban = counterpartyAccount != null ? counterpartyAccount.getIban() : null;
        String counterpartyName = counterpartyAccount != null
                ? counterpartyNamesByUserId.get(counterpartyAccount.getUser().getId())
                : null;

        return new TransactionResponse(
                transaction.getId(),
                sent ? "SENT" : "RECEIVED",
                transaction.getAmount(),
                counterpartyName,
                counterpartyIban,
                transaction.getCreatedAt(),
                transaction.getStatus(),
                transaction.getReferenceNote()
        );
    }

    private Long counterpartyAccountId(Transaction transaction, Long accountId) {
        return transaction.getSourceAccountId().equals(accountId)
                ? transaction.getDestinationAccountId()
                : transaction.getSourceAccountId();
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getIban(), account.getBalance(),
                account.getCurrency(), account.getStatus().name());
    }
}
