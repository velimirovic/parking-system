package com.sep.psp.merchant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    List<Merchant> findAllByOrderByCreatedAtAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Lock na redu merchanta - serijalizuje promene metoda placanja istog klijenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Merchant m where m.id = :id")
    Optional<Merchant> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('merchant_number_seq')", nativeQuery = true)
    long nextMerchantNumber();
}
