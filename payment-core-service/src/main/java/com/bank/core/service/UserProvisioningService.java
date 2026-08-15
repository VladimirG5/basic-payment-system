package com.bank.core.service;

import com.bank.core.domain.Account;
import com.bank.core.domain.AccountStatus;
import com.bank.core.domain.User;
import com.bank.core.dto.CreateUserRequest;
import com.bank.core.dto.UserCredentialsResult;
import com.bank.core.dto.UserProvisioningResult;
import com.bank.core.exception.DuplicateEmailException;
import com.bank.core.exception.UserNotFoundException;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

/**
 * Owns the users/accounts tables on behalf of auth-service, which has no direct database
 * access of its own - auth-service calls InternalUserController for every persistence
 * operation and keeps password hashing/verification and JWT issuance entirely on its side.
 */
@Service
public class UserProvisioningService {

    private static final String IBAN_PREFIX = "DE89370400440532";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserProvisioningService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public UserProvisioningResult createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = userRepository.save(new User(request.fullName(), request.email(), request.passwordHash()));
        Account account = accountRepository.save(new Account(user, generateUniqueIban(),
                BigDecimal.ZERO, DEFAULT_CURRENCY, AccountStatus.ACTIVE));

        return new UserProvisioningResult(user.getId(), user.getFullName(), user.getEmail(),
                account.getId(), account.getIban(), account.getBalance(), account.getCurrency(),
                account.getStatus().name());
    }

    public UserCredentialsResult findByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.forEmail(email));
        return toCredentialsResult(user);
    }

    public UserCredentialsResult findById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toCredentialsResult(user);
    }

    @Transactional
    public void updatePassword(Long userId, String passwordHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setPasswordHash(passwordHash);
        userRepository.save(user);
    }

    private UserCredentialsResult toCredentialsResult(User user) {
        return new UserCredentialsResult(user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash());
    }

    private String generateUniqueIban() {
        String iban;
        do {
            iban = IBAN_PREFIX + String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (accountRepository.existsByIban(iban));
        return iban;
    }
}
