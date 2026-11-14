package com.sep.parking.vehicle;

import com.sep.parking.common.exception.ApiException;
import com.sep.parking.session.ParkingSessionRepository;
import com.sep.parking.user.User;
import com.sep.parking.user.UserRepository;
import com.sep.parking.vehicle.VehicleDtos.VehicleRequest;
import com.sep.parking.vehicle.VehicleDtos.VehicleResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    /** Razuman limit po korisniku (zastita od zloupotrebe). */
    private static final int MAX_VEHICLES = 20;

    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final ParkingSessionRepository sessions;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, UserRepository users, ParkingSessionRepository sessions,
                          Clock clock) {
        this.vehicles = vehicles;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(UUID userId) {
        return vehicles.findActiveByUser(userId).stream().map(VehicleResponse::of).toList();
    }

    @Transactional
    public VehicleResponse create(UUID userId, VehicleRequest request) {
        PlateFormat.Plate plate = PlateFormat.parse(request.plate());
        List<Vehicle> existing = vehicles.findActiveByUser(userId);
        if (existing.size() >= MAX_VEHICLES) {
            throw ApiException.unprocessable("VEHICLE_LIMIT", "Možete sačuvati najviše " + MAX_VEHICLES + " vozila.");
        }
        if (vehicles.existsActivePlate(userId, plate.normalized())) {
            throw ApiException.conflict("PLATE_EXISTS", "Vozilo sa ovom registarskom oznakom je već sačuvano.");
        }
        User user = users.getReferenceById(userId);
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setUser(user);
        v.setPlate(plate.normalized());
        v.setDisplayPlate(plate.display());
        v.setBrand(clean(request.brand()));
        v.setModel(clean(request.model()));
        v.setCreatedAt(clock.instant());
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.makeDefault());
        if (makeDefault) {
            existing.forEach(e -> e.setDefaultVehicle(false));
        }
        v.setDefaultVehicle(makeDefault);
        try {
            vehicles.saveAndFlush(v);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PLATE_EXISTS", "Vozilo sa ovom registarskom oznakom je već sačuvano.");
        }
        return VehicleResponse.of(v);
    }

    @Transactional
    public VehicleResponse update(UUID userId, UUID vehicleId, VehicleRequest request) {
        Vehicle v = find(userId, vehicleId);
        PlateFormat.Plate plate = PlateFormat.parse(request.plate());
        if (!plate.normalized().equals(v.getPlate())) {
            if (sessions.hasOpenSession(vehicleId, clock.instant())) {
                throw ApiException.conflict("VEHICLE_IN_USE",
                        "Registarska oznaka se ne može menjati dok vozilo ima aktivnu sesiju ili plaćanje u toku.");
            }
            if (vehicles.existsActivePlate(userId, plate.normalized())) {
                throw ApiException.conflict("PLATE_EXISTS", "Vozilo sa ovom registarskom oznakom je već sačuvano.");
            }
        }
        v.setPlate(plate.normalized());
        v.setDisplayPlate(plate.display());
        v.setBrand(clean(request.brand()));
        v.setModel(clean(request.model()));
        if (Boolean.TRUE.equals(request.makeDefault()) && !v.isDefaultVehicle()) {
            vehicles.findActiveByUser(userId).forEach(e -> e.setDefaultVehicle(e.getId().equals(vehicleId)));
        }
        return VehicleResponse.of(v);
    }

    /** "Meko" brisanje - vozilo ostaje u istoriji parkiranja. */
    @Transactional
    public void delete(UUID userId, UUID vehicleId) {
        Vehicle v = find(userId, vehicleId);
        Instant now = clock.instant();
        if (sessions.hasOpenSession(vehicleId, now)) {
            throw ApiException.conflict("VEHICLE_IN_USE",
                    "Vozilo ima aktivnu parking sesiju ili plaćanje u toku i ne može se ukloniti.");
        }
        boolean wasDefault = v.isDefaultVehicle();
        v.setDeletedAt(now);
        v.setDefaultVehicle(false);
        if (wasDefault) {
            vehicles.findActiveByUser(userId).stream()
                    .filter(e -> !e.getId().equals(vehicleId))
                    .findFirst()
                    .ifPresent(e -> e.setDefaultVehicle(true));
        }
    }

    private Vehicle find(UUID userId, UUID vehicleId) {
        return vehicles.findActiveByIdAndUser(vehicleId, userId)
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Vozilo nije pronađeno."));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
