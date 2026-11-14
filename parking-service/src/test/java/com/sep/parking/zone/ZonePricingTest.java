package com.sep.parking.zone;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep.parking.common.config.ParkingTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ZonePricingTest {

    private final ZonePricing pricing = new ZonePricing();

    /** Zona 1: pon-pet 07-21 120 RSD/h, subota 07-14 90 RSD/h, nedelja bez naplate. */
    private static ParkingZone zone1() {
        ParkingZone zone = new ParkingZone();
        zone.setId(1L);
        zone.setMaxDurationMinutes(120);
        zone.setMinDurationMinutes(15);
        zone.setDurationStepMinutes(15);
        zone.getTariffs().add(tariff(zone, 1, 5, 420, 1260, "120.00"));
        zone.getTariffs().add(tariff(zone, 6, 6, 420, 840, "90.00"));
        return zone;
    }

    private static ZoneTariff tariff(ParkingZone zone, int from, int to, int start, int end, String price) {
        ZoneTariff t = new ZoneTariff();
        t.setZone(zone);
        t.setLabel("t");
        t.setDayFrom((short) from);
        t.setDayTo((short) to);
        t.setStartMinute(start);
        t.setEndMinute(end);
        t.setPricePerHour(new BigDecimal(price));
        return t;
    }

    private static Instant local(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(ParkingTime.ZONE).toInstant();
    }

    @Test
    void weekdayPriceIsRateTimesDuration() {
        // ponedeljak 16.11.2026. 10:00, 90 min x 120 RSD/h = 180,00
        ZonePricing.Price p = pricing.price(zone1(), local("2026-11-16T10:00"), 90, 120);
        assertThat(p.amount()).isEqualByComparingTo("180.00");
        assertThat(p.rateAtStart()).isEqualByComparingTo("120.00");
        assertThat(p.fullyCharged(90)).isTrue();
        assertThat(p.chargeableMinutes()).isEqualTo(120);
    }

    @Test
    void sessionCannotLastPastChargingHours() {
        // subota 13:30 - naplata se zavrsava u 14:00
        ZonePricing.Price p = pricing.price(zone1(), local("2026-11-14T13:30"), 60, 120);
        assertThat(p.amount()).isNull();
        assertThat(p.fullyCharged(60)).isFalse();
        assertThat(p.chargeableMinutes()).isEqualTo(30);
        assertThat(p.chargingEndsAt()).isEqualTo(local("2026-11-14T14:00"));
    }

    @Test
    void noChargingOnSunday() {
        ZonePricing.Price p = pricing.price(zone1(), local("2026-11-15T10:00"), 30, 120);
        assertThat(p.rateAtStart()).isNull();
        assertThat(p.chargeableMinutes()).isZero();
        assertThat(p.amount()).isNull();
    }

    @Test
    void priceAcrossTwoTariffsIsSummedPerMinute() {
        ParkingZone zone = new ParkingZone();
        zone.setMaxDurationMinutes(240);
        zone.getTariffs().add(tariff(zone, 1, 7, 420, 1260, "60.00"));
        zone.getTariffs().add(tariff(zone, 1, 7, 1260, 1440, "40.00"));
        zone.getTariffs().add(tariff(zone, 1, 7, 0, 420, "40.00"));
        // 20:30 - 21:30: 30 min po 60 + 30 min po 40 = 30 + 20 = 50,00
        ZonePricing.Price p = pricing.price(zone, local("2026-11-16T20:30"), 60, 240);
        assertThat(p.amount()).isEqualByComparingTo("50.00");
        // preko ponoci je i dalje u naplati
        ZonePricing.Price night = pricing.price(zone, local("2026-11-16T23:30"), 60, 240);
        assertThat(night.amount()).isEqualByComparingTo("40.00");
    }
}
