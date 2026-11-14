package com.sep.parking.zone;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingZoneRepository extends JpaRepository<ParkingZone, Long> {

    @EntityGraph(attributePaths = "tariffs")
    @Query("select z from ParkingZone z order by z.sortOrder asc, z.id asc")
    List<ParkingZone> findAllOrdered();

    @EntityGraph(attributePaths = "tariffs")
    @Query("select z from ParkingZone z where z.id = :id")
    Optional<ParkingZone> findWithTariffs(@Param("id") Long id);
}
