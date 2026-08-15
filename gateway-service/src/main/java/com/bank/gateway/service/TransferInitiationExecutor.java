package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.dto.TransferInitiateRequest;
import com.bank.gateway.dto.TransferInitiateResponse;
import com.bank.gateway.exception.AccountAccessDeniedException;
import com.bank.gateway.exception.AccountNotFoundException;
import com.bank.gateway.exception.InsufficientFundsException;
import com.bank.gateway.exception.InvalidTransferException;
import com.bank.gateway.repository.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * Blocking validation for transfer initiation, reading balances directly off the shared
 * accounts table (same shortcut as AccountService's read paths - see the domain package-info
 * for why gateway talks to the DB directly instead of calling payment-core-service).
 * No write happens here: the actual debit/credit only occurs in payment-core-service once the
 * OTP is confirmed, so this stays outside a @Transactional boundary.
 */
@Component
class TransferInitiationExecutor {

    private final AccountRepository accountRepository;
    private final OtpChallengeService otpChallengeService;

    TransferInitiationExecutor(AccountRepository accountRepository, OtpChallengeService otpChallengeService) {
        this.accountRepository = accountRepository;
        this.otpChallengeService = otpChallengeService;
    }

    TransferInitiateResponse execute(Long userId, TransferInitiateRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new InvalidTransferException("Source and destination account must be different");
        }

        Account source = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.sourceAccountId()));

        if (!source.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(request.sourceAccountId());
        }

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(request.amount(), source.getBalance());
        }

        OtpChallengeService.CreatedOtpChallenge challenge = otpChallengeService.create(
                userId, request.sourceAccountId(), request.destinationAccountId(),
                request.amount(), request.currency(), request.description());
        otpChallengeService.send(challenge);

        return new TransferInitiateResponse(challenge.challengeId(), challenge.expiresAt(), "OTP_REQUIRED");
    }
}
