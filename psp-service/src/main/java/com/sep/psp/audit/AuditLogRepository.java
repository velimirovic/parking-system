package com.sep.psp.audit;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Optional<AuditLog> findTopByOrderBySeqDesc();

    @Query("""
            select a from AuditLog a
            where (:action = '' or a.action = :action)
              and (:outcome is null or a.outcome = :outcome)
              and (:actor = '' or lower(coalesce(a.actorId, '')) like concat('%', :actor, '%'))
            order by a.seq desc
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("outcome") AuditOutcome outcome,
                          @Param("actor") String actor,
                          Pageable pageable);

    @Query("select a from AuditLog a order by a.seq asc")
    Stream<AuditLog> streamAllInOrder();
}
