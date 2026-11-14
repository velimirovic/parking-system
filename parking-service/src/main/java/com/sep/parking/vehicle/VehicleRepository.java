package com.sep.parking.vehicle;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("select v from Vehicle v where v.user.id = :userId and v.deletedAt is null "
            + "order by v.defaultVehicle desc, v.createdAt asc")
    List<Vehicle> findActiveByUser(@Param("userId") UUID userId);

    @Query("select v from Vehicle v where v.id = :id and v.user.id = :userId and v.deletedAt is null")
    Optional<Vehicle> findActiveByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Zakljucava red vozila do kraja transakcije: dva paralelna zahteva za isto vozilo
     * (npr. dvostruki klik) se izvrsavaju jedan za drugim.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id and v.user.id = :userId and v.deletedAt is null")
    Optional<Vehicle> lockActiveByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select count(v) > 0 from Vehicle v where v.user.id = :userId and v.plate = :plate and v.deletedAt is null")
    boolean existsActivePlate(@Param("userId") UUID userId, @Param("plate") String plate);

    @Query("select v from Vehicle v where v.user.id = :userId order by v.createdAt asc")
    List<Vehicle> findAllByUserIncludingDeleted(@Param("userId") UUID userId);
}
