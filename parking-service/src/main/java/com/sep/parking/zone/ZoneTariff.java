package com.sep.parking.zone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Radno vreme naplate i cena po satu za opseg dana (1 = ponedeljak ... 7 = nedelja). */
@Entity
@Table(name = "zone_tariff")
@Getter
@Setter
@NoArgsConstructor
public class ZoneTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private ParkingZone zone;

    @Column(nullable = false)
    private String label;

    @Column(name = "day_from", nullable = false)
    private short dayFrom;

    @Column(name = "day_to", nullable = false)
    private short dayTo;

    /** minuti od ponoci, interval [startMinute, endMinute) */
    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    @Column(name = "price_per_hour", nullable = false)
    private BigDecimal pricePerHour;

    public boolean covers(DayOfWeek day, int minuteOfDay) {
        int d = day.getValue();
        return d >= dayFrom && d <= dayTo && minuteOfDay >= startMinute && minuteOfDay < endMinute;
    }
}
