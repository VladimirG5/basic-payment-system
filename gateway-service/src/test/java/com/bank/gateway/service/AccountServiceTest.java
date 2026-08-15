package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.domain.AccountStatus;
import com.bank.gateway.domain.Transaction;
import com.bank.gateway.domain.User;
import com.bank.gateway.dto.TransactionResponse;
import com.bank.gateway.exception.AccountAccessDeniedException;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.repository.AccountRepository;
import com.bank.gateway.repository.TransactionRepository;
import com.bank.gateway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountService accountService =
            new AccountService(accountRepository, transactionRepository, userRepository);

    private static User userWithId(long id, String fullName) {
        User user = new User(fullName, fullName.toLowerCase() + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Account accountWithId(long id, User owner, String iban, BigDecimal balance) {
        Account account = new Account(owner, iban, balance, "USD", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void getAccountReturnsResponseWhenOwnedByCaller() {
        User owner = userWithId(1L, "Alice");
        Account account = accountWithId(10L, owner, "DE001", new BigDecimal("100.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        StepVerifier.create(accountService.getAccount(1L, 10L))
                .assertNext(response -> {
                    assertEquals(10L, response.accountId());
                    assertEquals("DE001", response.iban());
                    assertEquals("ACTIVE", response.status());
                })
                .verifyComplete();
    }

    @Test
    void getAccountThrowsWhenAccountDoesNotExist() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(accountService.getAccount(1L, 99L))
                .expectError(AccountNotFoundException.class)
                .verify();
    }

    @Test
    void getAccountThrowsWhenOwnedByAnotherUser() {
        User owner = userWithId(2L, "Bob");
        Account account = accountWithId(10L, owner, "DE002", new BigDecimal("50.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        StepVerifier.create(accountService.getAccount(1L, 10L))
                .expectError(AccountAccessDeniedException.class)
                .verify();
    }

    @Test
    void getMyAccountResolvesAccountFromCallerId() {
        User owner = userWithId(1L, "Alice");
        Account account = accountWithId(10L, owner, "DE001", new BigDecimal("100.00"));
        when(accountRepository.findByUser_Id(1L)).thenReturn(Optional.of(account));

        StepVerifier.create(accountService.getMyAccount(1L))
                .assertNext(response -> assertEquals(10L, response.accountId()))
                .verifyComplete();
    }

    @Test
    void getMyAccountThrowsWhenCallerHasNoAccount() {
        when(accountRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        StepVerifier.create(accountService.getMyAccount(1L))
                .expectError(AccountNotFoundException.class)
                .verify();
    }

    @Test
    void getTransactionsThrowsWhenAccountDoesNotExist() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(accountService.getTransactions(1L, 99L))
                .expectError(AccountNotFoundException.class)
                .verify();
    }

    @Test
    void getTransactionsThrowsWhenNotOwnedByCaller() {
        User owner = userWithId(2L, "Bob");
        Account account = accountWithId(10L, owner, "DE002", new BigDecimal("50.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        StepVerifier.create(accountService.getTransactions(1L, 10L))
                .expectError(AccountAccessDeniedException.class)
                .verify();
    }

    @Test
    void getTransactionsResolvesCounterpartyNameAndDirectionForBothSides() {
        User alice = userWithId(1L, "Alice");
        User bob = userWithId(2L, "Bob");
        Account aliceAccount = accountWithId(10L, alice, "DE001", new BigDecimal("925.00"));
        Account bobAccount = accountWithId(20L, bob, "DE002", new BigDecimal("75.00"));

        when(accountRepository.findById(10L)).thenReturn(Optional.of(aliceAccount));

        Transaction sent = mock(Transaction.class);
        when(sent.getId()).thenReturn(1L);
        when(sent.getSourceAccountId()).thenReturn(10L);
        when(sent.getDestinationAccountId()).thenReturn(20L);
        when(sent.getAmount()).thenReturn(new BigDecimal("75.00"));
        when(sent.getStatus()).thenReturn("SUCCESS");

        Transaction received = mock(Transaction.class);
        when(received.getId()).thenReturn(2L);
        when(received.getSourceAccountId()).thenReturn(20L);
        when(received.getDestinationAccountId()).thenReturn(10L);
        when(received.getAmount()).thenReturn(new BigDecimal("30.00"));
        when(received.getStatus()).thenReturn("SUCCESS");

        when(transactionRepository.findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(10L, 10L))
                .thenReturn(List.of(sent, received));
        when(accountRepository.findAllById(List.of(20L))).thenReturn(List.of(bobAccount));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(bob));

        StepVerifier.create(accountService.getTransactions(1L, 10L))
                .assertNext(list -> {
                    List<TransactionResponse> responses = (List<TransactionResponse>) (List<?>) list;
                    assertEquals(2, responses.size());
                    TransactionResponse sentResponse = responses.get(0);
                    assertEquals("SENT", sentResponse.type());
                    assertEquals("Bob", sentResponse.counterpartyName());
                    assertEquals("DE002", sentResponse.counterpartyAccount());

                    TransactionResponse receivedResponse = responses.get(1);
                    assertEquals("RECEIVED", receivedResponse.type());
                    assertEquals("Bob", receivedResponse.counterpartyName());
                })
                .verifyComplete();
    }
}
