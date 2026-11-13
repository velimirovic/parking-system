package com.sep.bank.card;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByPanToken(String panToken);
}
