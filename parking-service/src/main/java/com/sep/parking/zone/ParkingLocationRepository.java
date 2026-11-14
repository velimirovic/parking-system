package com.sep.parking.zone;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, Long> {

    List<ParkingLocation> findByZoneIdOrderByNameAsc(Long zoneId);

    List<ParkingLocation> findAllByOrderByNameAsc();
}
