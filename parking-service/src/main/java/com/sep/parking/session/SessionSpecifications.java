package com.sep.parking.session;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filteri istorije (period, vozilo, status) - svaki se primenjuje samo ako je zadat. */
public final class SessionSpecifications {

    private SessionSpecifications() {
    }

    public static Specification<ParkingSession> ofUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<ParkingSession> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), to));
    }

    public static Specification<ParkingSession> vehicle(UUID vehicleId) {
        return (root, query, cb) -> cb.equal(root.get("vehicle").get("id"), vehicleId);
    }

    public static Specification<ParkingSession> status(SessionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
