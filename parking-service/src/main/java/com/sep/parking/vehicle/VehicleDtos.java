package com.sep.parking.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {
    }

    public record VehicleRequest(
            @NotBlank(message = "Registarska oznaka je obavezna.") @Size(max = 20) String plate,
            @Size(max = 40, message = "Marka je predugačka.") String brand,
            @Size(max = 40, message = "Model je predugačak.") String model,
            Boolean makeDefault) {
    }

    public record VehicleResponse(UUID id, String plate, String brand, String model, String label,
                                  boolean isDefault, Instant createdAt) {

        public static VehicleResponse of(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getDisplayPlate(), v.getBrand(), v.getModel(), v.label(),
                    v.isDefaultVehicle(), v.getCreatedAt());
        }
    }
}
