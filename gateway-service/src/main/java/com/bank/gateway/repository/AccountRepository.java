package com.bank.gateway.repository;

import com.bank.gateway.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByIban(String iban);

    Optional<Account> findByUser_Id(Long userId);
}
