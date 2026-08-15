package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.domain.AccountStatus;
import com.bank.gateway.domain.User;
import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.exception.AccountAccessDeniedException;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.exception.InsufficientFundsException;
import com.bank.gateway.exception.InvalidTransferException;
import com.bank.gateway.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferInitiationExecutorTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final OtpChallengeService otpChallengeService = mock(OtpChallengeService.class);
    private final TransferInitiationExecutor executor =
            new TransferInitiationExecutor(accountRepository, otpChallengeService);

    private static Account accountWithId(long id, User owner, BigDecimal balance) {
        Account account = new Account(owner, "DE00" + id, balance, "USD", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static User userWithId(long id) {
        User user = new User("Name", "name" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void rejectsATransferToTheSameAccount() {
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 10L, BigDecimal.TEN, "USD", null);

        assertThrows(InvalidTransferException.class, () -> executor.execute(1L, request));
    }

    @Test
    void rejectsWhenSourceAccountDoesNotExist() {
        when(accountRepository.findById(10L)).thenReturn(Optional.empty());
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 20L, BigDecimal.TEN, "USD", null);

        assertThrows(AccountNotFoundException.class, () -> executor.execute(1L, request));
    }

    @Test
    void rejectsWhenSourceAccountIsNotOwnedByCaller() {
        Account source = accountWithId(10L, userWithId(2L), new BigDecimal("100"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(source));
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 20L, BigDecimal.TEN, "USD", null);

        assertThrows(AccountAccessDeniedException.class, () -> executor.execute(1L, request));
    }

    @Test
    void rejectsWhenBalanceIsBelowTheRequestedAmount() {
        Account source = accountWithId(10L, userWithId(1L), new BigDecimal("5.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(source));
        TransferInitiateRequest request = new TransferInitiateRequest(10L, 20L, new BigDecimal("75.00"), "USD", null);

        assertThrows(InsufficientFundsException.class, () -> executor.execute(1L, request));
    }

    @Test
    void createsAndSendsAnOtpChallengeOnSuccess() {
        Account source = accountWithId(10L, userWithId(1L), new BigDecimal("1000.00"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(source));

        OtpChallengeService.CreatedOtpChallenge challenge = new OtpChallengeService.CreatedOtpChallenge(
                "challenge-1", "123456", Instant.now().plusSeconds(180));
        when(otpChallengeService.create(eq(1L), eq(10L), eq(20L), eq(new BigDecimal("75.00")), eq("USD"), any()))
                .thenReturn(challenge);

        TransferInitiateRequest request = new TransferInitiateRequest(10L, 20L, new BigDecimal("75.00"), "USD", "rent");
        TransferInitiateResponse response = executor.execute(1L, request);

        assertEquals("challenge-1", response.challengeId());
        assertEquals("OTP_REQUIRED", response.status());
        verify(otpChallengeService).send(challenge);
    }
}
