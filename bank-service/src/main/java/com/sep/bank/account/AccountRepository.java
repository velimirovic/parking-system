package com.sep.bank.account;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Atomska rezervacija sredstava (dizajn 5.13): uslov i izmena su u jednom UPDATE-u, pa dva paralelna
     * placanja sa istog racuna ne mogu zajedno potrositi vise od raspolozivog stanja.
     *
     * @return 1 ako je rezervacija uspela, 0 ako nema dovoljno sredstava
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update account
               set reserved = reserved + :amount, version = version + 1
             where id = :id and balance - reserved >= :amount
            """, nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /** Oslobadjanje rezervacije (refundacija rezervisanog iznosa). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update account
               set reserved = reserved - :amount, version = version + 1
             where id = :id and reserved >= :amount
            """, nativeQuery = true)
    int release(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
