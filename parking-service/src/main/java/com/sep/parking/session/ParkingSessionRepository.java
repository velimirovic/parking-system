package com.sep.parking.session;

import com.sep.parking.transaction.TransactionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID>,
        JpaSpecificationExecutor<ParkingSession> {

    /**
     * Koliko dugo nalog bez PSP roka (npr. PSP nije bio dostupan) blokira vozilo.
     * Jednako roku checkout-a na PSP-u (15 min).
     */
    Duration PENDING_WITHOUT_EXPIRY_TTL = Duration.ofMinutes(15);

    /**
     * Vozilo je "zauzeto" ako ima aktivnu sesiju koja jos nije istekla ili nalog cije placanje je
     * jos otvoreno (korisnik je na PSP-u). Nalog cije je placanje isteklo vise ne blokira vozilo;
     * njegov konacan status se uskladjuje proverom statusa na PSP-u.
     */
    @Query("""
            select count(s) > 0 from ParkingSession s
            where s.vehicle.id = :vehicleId
              and ((s.status = :active and s.endsAt > :now)
                or (s.status = :pending and exists (
                      select 1 from PaymentTransaction t
                      where t.session = s and t.status in :openStatuses
                        and (t.paymentExpiresAt > :now
                             or (t.paymentExpiresAt is null and t.createdAt > :pendingCutoff)))))
            """)
    boolean existsOpen(@Param("vehicleId") UUID vehicleId,
                       @Param("now") Instant now,
                       @Param("pendingCutoff") Instant pendingCutoff,
                       @Param("active") SessionStatus active,
                       @Param("pending") SessionStatus pending,
                       @Param("openStatuses") Collection<TransactionStatus> openStatuses);

    default boolean hasOpenSession(UUID vehicleId, Instant now) {
        return existsOpen(vehicleId, now, now.minus(PENDING_WITHOUT_EXPIRY_TTL), SessionStatus.ACTIVE,
                SessionStatus.PENDING_PAYMENT, List.of(TransactionStatus.INIT, TransactionStatus.PENDING));
    }

    /** Aktivne sesije korisnika i nalozi koji jos cekaju potvrdu placanja. */
    @EntityGraph(attributePaths = {"zone", "vehicle", "location"})
    @Query("""
            select s from ParkingSession s
            where s.user.id = :userId
              and ((s.status = :active and s.endsAt > :now)
                or (s.status = :pending and s.createdAt > :pendingSince))
            order by s.createdAt desc
            """)
    List<ParkingSession> findCurrent(@Param("userId") UUID userId,
                                     @Param("now") Instant now,
                                     @Param("pendingSince") Instant pendingSince,
                                     @Param("active") SessionStatus active,
                                     @Param("pending") SessionStatus pending);

    @EntityGraph(attributePaths = {"zone", "vehicle", "location"})
    @Query("select s from ParkingSession s where s.id = :id and s.user.id = :userId")
    Optional<ParkingSession> findForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Da li vozilo ima drugu aktivnu sesiju koja jos traje (aktivacija ne sme napraviti preklapanje). */
    @Query("""
            select count(s) > 0 from ParkingSession s
            where s.vehicle.id = :vehicleId and s.id <> :excludeId and s.status = :active and s.endsAt > :now
            """)
    boolean existsOtherActive(@Param("vehicleId") UUID vehicleId,
                              @Param("excludeId") UUID excludeId,
                              @Param("now") Instant now,
                              @Param("active") SessionStatus active);

    @Query("select s.id from ParkingSession s where s.status = :status and s.endsAt <= :now")
    List<UUID> findIdsDue(@Param("status") SessionStatus status, @Param("now") Instant now);
}
