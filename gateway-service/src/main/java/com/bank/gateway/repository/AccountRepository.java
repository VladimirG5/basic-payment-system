package com.bank.gateway.repository;

import com.bank.gateway.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByIban(String iban);
}
