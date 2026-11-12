package com.sep.bank.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankCustomerRepository extends JpaRepository<BankCustomer, UUID> {

    Optional<BankCustomer> findByUsername(String username);
}
