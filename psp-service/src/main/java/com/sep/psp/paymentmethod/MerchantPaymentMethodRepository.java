package com.sep.psp.paymentmethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPaymentMethodRepository extends JpaRepository<MerchantPaymentMethod, UUID> {

    List<MerchantPaymentMethod> findByMerchantId(UUID merchantId);

    List<MerchantPaymentMethod> findByMerchantIdIn(List<UUID> merchantIds);

    Optional<MerchantPaymentMethod> findByMerchantIdAndPaymentMethodId(UUID merchantId, UUID paymentMethodId);

    /** Metode koje klijent zaista moze da koristi: pretplata aktivna i plugin globalno aktivan. */
    @Query("""
            select count(mpm) from MerchantPaymentMethod mpm
            where mpm.merchantId = :merchantId and mpm.active = true and mpm.paymentMethod.active = true
            """)
    long countUsable(@Param("merchantId") UUID merchantId);

    /**
     * Klijenti kojima je data metoda jedina upotrebljiva - globalna deaktivacija plugin-a
     * bi ih ostavila bez ijednog nacina placanja.
     */
    @Query("""
            select mpm.merchantId from MerchantPaymentMethod mpm
            where mpm.paymentMethod.id = :methodId and mpm.active = true
              and not exists (
                select 1 from MerchantPaymentMethod other
                where other.merchantId = mpm.merchantId and other.active = true
                  and other.paymentMethod.active = true and other.paymentMethod.id <> :methodId)
            """)
    List<UUID> findMerchantsDependingOnlyOn(@Param("methodId") UUID methodId);
}
