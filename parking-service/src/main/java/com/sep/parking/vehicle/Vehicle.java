package com.sep.parking.vehicle;

import com.sep.parking.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Normalizovana oznaka (npr. BG123AB). */
    @Column(nullable = false)
    private String plate;

    /** Oznaka za prikaz (npr. BG 123-AB). */
    @Column(name = "display_plate", nullable = false)
    private String displayPlate;

    private String brand;

    private String model;

    @Column(name = "is_default", nullable = false)
    private boolean defaultVehicle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String label() {
        String b = brand == null ? "" : brand;
        String m = model == null ? "" : model;
        return (b + " " + m).trim();
    }
}
