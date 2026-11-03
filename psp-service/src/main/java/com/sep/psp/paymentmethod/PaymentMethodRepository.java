package com.sep.psp.paymentmethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    Optional<PaymentMethod> findByCode(String code);

    boolean existsByCode(String code);

    List<PaymentMethod> findAllByOrderByCreatedAtAscCodeAsc();
}
