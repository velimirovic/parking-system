package com.sep.bank.paymentrequest;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcquirerMerchantRepository extends JpaRepository<AcquirerMerchant, UUID> {

    Optional<AcquirerMerchant> findByMerchantCode(String merchantCode);
}
