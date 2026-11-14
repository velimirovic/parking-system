package com.sep.parking.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nepromenljiv zapis jedne promene statusa sesije (trag za istoriju i reviziju). */
@Entity
@Table(name = "session_status_log")
@Getter
@Setter
@NoArgsConstructor
public class SessionStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "from_status", updatable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;

    @Column(nullable = false, updatable = false)
    private String event;

    @Column(updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StatusActor actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
