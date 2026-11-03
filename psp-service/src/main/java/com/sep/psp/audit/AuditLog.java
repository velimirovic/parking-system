package com.sep.psp.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only zapis (PCI DSS 10.2). Svaki zapis sadrzi hash prethodnog, pa se
 * naknadna izmena bilo kog reda otkriva verifikacijom lanca (10.3.4).
 */
@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(insertable = false, updatable = false)
    private Long seq;

    private Instant occurredAt;
    private String actorType;
    private String actorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    private AuditOutcome outcome;

    private String details;
    private String prevHash;
    private String hash;
}
