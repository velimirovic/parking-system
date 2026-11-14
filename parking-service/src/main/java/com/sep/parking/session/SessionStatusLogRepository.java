package com.sep.parking.session;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionStatusLogRepository extends JpaRepository<SessionStatusLog, Long> {

    List<SessionStatusLog> findBySessionIdInOrderByOccurredAtAscIdAsc(Collection<UUID> sessionIds);
}
