package com.sep.bank.transaction;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    @Query("select t from BankTransaction t where t.paymentRequestId = :requestId and t.type <> 'REFUND'")
    Optional<BankTransaction> findPayment(@Param("requestId") UUID requestId);
}
