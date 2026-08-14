package com.bank.gateway.service;

import com.bank.gateway.domain.Account;
import com.bank.gateway.domain.AccountStatus;
import com.bank.gateway.domain.User;
import com.bank.gateway.dto.RegisterRequest;
import com.bank.gateway.dto.RegisterResponse;
import com.bank.gateway.exception.DuplicateEmailException;
import com.bank.gateway.repository.AccountRepository;
import com.bank.gateway.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

/**
 * A separate bean rather than a private method on AuthService, purely so @Transactional takes
 * effect: Spring's proxy-based transactions don't apply to self-invoked methods, and the
 * user-insert + account-insert pair here must commit or roll back together.
 */
@Component
class RegistrationExecutor {

    private static final String IBAN_PREFIX = "DE89370400440532";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    RegistrationExecutor(UserRepository userRepository, AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    RegisterResponse execute(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = userRepository.save(new User(request.fullName(), request.email(),
                passwordEncoder.encode(request.password())));

        Account account = accountRepository.save(new Account(user, generateUniqueIban(),
                BigDecimal.ZERO, DEFAULT_CURRENCY, AccountStatus.ACTIVE));

        return new RegisterResponse(user.getId(), user.getFullName(), user.getEmail(),
                account.getId(), account.getIban(), account.getBalance(), account.getCurrency(),
                account.getStatus().name());
    }

    private String generateUniqueIban() {
        String iban;
        do {
            iban = IBAN_PREFIX + String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (accountRepository.existsByIban(iban));
        return iban;
    }
}
