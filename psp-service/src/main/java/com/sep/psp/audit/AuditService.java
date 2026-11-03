package com.sep.psp.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.common.crypto.Hashing;
import com.sep.psp.common.web.RequestContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * Upis i verifikacija audit loga. Zapisi se ulancavaju hash-om prethodnog zapisa,
 * a upis se serijalizuje advisory lock-om da bi lanac ostao linearan i pri vise instanci PSP-a.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String GENESIS_HASH = "0".repeat(64);
    private static final long CHAIN_LOCK_KEY = 7_340_001L;

    private final AuditLogRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public static final String ACTOR_USER = "PSP_USER";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    /** Dogadjaj prijavljenog korisnika portala. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userEvent(String action, String resourceType, String resourceId, AuditOutcome outcome, String details) {
        record(ACTOR_USER, RequestContext.userEmail(), action, resourceType, resourceId, outcome, details);
    }

    /**
     * Zapis se commit-uje u posebnoj transakciji, pa ostaje sacuvan i kada se
     * poslovna operacija (npr. neuspela prijava) zavrsi greskom.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorType, String actorId, String action, String resourceType, String resourceId,
                       AuditOutcome outcome, String details) {
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(:key)) l")
                .setParameter("key", CHAIN_LOCK_KEY)
                .getSingleResult();

        String prevHash = repository.findTopByOrderBySeqDesc().map(AuditLog::getHash).orElse(GENESIS_HASH);

        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        entry.setActorType(actorType);
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setSourceIp(RequestContext.clientIp());
        entry.setOutcome(outcome);
        entry.setDetails(details);
        entry.setPrevHash(prevHash);
        entry.setHash(computeHash(entry));
        entityManager.persist(entry);
    }

    @Transactional(readOnly = true)
    public ChainVerification verifyChain() {
        long checked = 0;
        String expectedPrev = GENESIS_HASH;
        try (Stream<AuditLog> stream = repository.streamAllInOrder()) {
            Iterator<AuditLog> iterator = stream.iterator();
            while (iterator.hasNext()) {
                AuditLog entry = iterator.next();
                checked++;
                if (!Objects.equals(entry.getPrevHash(), expectedPrev)
                        || !Objects.equals(entry.getHash(), computeHash(entry))) {
                    return new ChainVerification(false, checked, entry.getSeq());
                }
                expectedPrev = entry.getHash();
                entityManager.detach(entry);
            }
        }
        return new ChainVerification(true, checked, null);
    }

    static String computeHash(AuditLog entry) {
        String canonical = String.join("|",
                entry.getPrevHash(),
                entry.getOccurredAt().toString(),
                nullSafe(entry.getActorType()),
                nullSafe(entry.getActorId()),
                nullSafe(entry.getAction()),
                nullSafe(entry.getResourceType()),
                nullSafe(entry.getResourceId()),
                nullSafe(entry.getSourceIp()),
                entry.getOutcome().name(),
                nullSafe(entry.getDetails()));
        return Hashing.sha256Hex(canonical);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record ChainVerification(boolean valid, long checkedEntries, Long brokenAtSeq) {
    }
}
