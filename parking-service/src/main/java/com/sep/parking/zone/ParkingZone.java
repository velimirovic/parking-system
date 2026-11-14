package com.sep.parking.zone;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parking_zone")
@Getter
@Setter
@NoArgsConstructor
public class ParkingZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String area;

    private String description;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "min_duration_minutes", nullable = false)
    private int minDurationMinutes;

    @Column(name = "max_duration_minutes", nullable = false)
    private int maxDurationMinutes;

    @Column(name = "duration_step_minutes", nullable = false)
    private int durationStepMinutes;

    @Column(name = "extension_allowed", nullable = false)
    private boolean extensionAllowed;

    /** null = neograniceno */
    @Column(name = "max_extensions")
    private Integer maxExtensions;

    /** null = bez ogranicenja ukupnog trajanja */
    @Column(name = "max_total_minutes")
    private Integer maxTotalMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL)
    @OrderBy("dayFrom asc, startMinute asc")
    private List<ZoneTariff> tariffs = new ArrayList<>();

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL)
    @OrderBy("name asc")
    private List<ParkingLocation> locations = new ArrayList<>();

    /** Tarifa koja vazi u datom lokalnom trenutku; prazno = u tom trenutku nema naplate. */
    public Optional<ZoneTariff> tariffAt(ZonedDateTime localTime) {
        DayOfWeek day = localTime.getDayOfWeek();
        int minute = localTime.getHour() * 60 + localTime.getMinute();
        return tariffs.stream().filter(t -> t.covers(day, minute)).findFirst();
    }
}
