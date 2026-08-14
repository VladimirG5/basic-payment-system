package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.dto.AccountResponse;
import com.bank.gateway.exception.AccountAccessDeniedException;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.repository.AccountRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Mono<AccountResponse> getAccount(Long userId, Long accountId) {
        return Mono.fromCallable(() -> getAccountBlocking(userId, accountId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AccountResponse getAccountBlocking(Long userId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(accountId);
        }

        return new AccountResponse(account.getId(), account.getIban(), account.getBalance(),
                account.getCurrency(), account.getStatus().name());
    }
}
