package com.sep.parking.zone;

import com.sep.parking.common.config.ParkingTime;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Obracun cene parkiranja po pravilima zone.
 *
 * <p>Cena se racuna minut po minut: svaki minut se naplacuje po tarifi koja u tom minutu vazi
 * (cena/h / 60), a zbir se zaokruzuje na 2 decimale. Tako je obracun tacan i kada sesija
 * prelazi granicu dve tarife (npr. dnevna -> nocna). Ako neki minut sesije pada van radnog
 * vremena naplate, sesija nije dozvoljena (pravilo zone: "sesija ne moze trajati van radnog
 * vremena naplate").
 */
@Component
public class ZonePricing {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    /**
     * @param amount            iznos za ceo period (2 decimale); null ako period nije u potpunosti u naplati
     * @param rateAtStart       cena/h koja vazi na pocetku perioda (null = trenutno nema naplate)
     * @param chargeableMinutes koliko minuta od pocetka je neprekidno u radnom vremenu naplate
     *                          (ograniceno na {@code limit}) - najduze trajanje koje se moze kupiti
     * @param chargingEndsAt    kraj neprekidnog radnog vremena naplate od pocetka (null ako je dalje od limita)
     */
    public record Price(BigDecimal amount, BigDecimal rateAtStart, int chargeableMinutes, Instant chargingEndsAt) {

        public boolean fullyCharged(int durationMinutes) {
            return amount != null && chargeableMinutes >= durationMinutes;
        }
    }

    /**
     * @param start           pocetak (zaokruzuje se na minut)
     * @param durationMinutes trajanje koje se kupuje
     * @param limit           do koliko minuta unapred se trazi kraj naplate (npr. max trajanje zone)
     */
    public Price price(ParkingZone zone, Instant start, int durationMinutes, int limit) {
        ZonedDateTime local = start.truncatedTo(ChronoUnit.MINUTES).atZone(ParkingTime.ZONE);
        int horizon = Math.max(durationMinutes, limit);

        BigDecimal rateAtStart = zone.tariffAt(local).map(ZoneTariff::getPricePerHour).orElse(null);
        BigDecimal sum = BigDecimal.ZERO;
        int chargeable = 0;
        boolean contiguous = true;
        Instant chargingEndsAt = null;

        for (int i = 0; i < horizon; i++) {
            ZonedDateTime minute = local.plusMinutes(i);
            Optional<ZoneTariff> tariff = zone.tariffAt(minute);
            if (tariff.isEmpty()) {
                if (contiguous) {
                    chargingEndsAt = minute.toInstant();
                }
                contiguous = false;
                if (i >= durationMinutes) {
                    break;
                }
                continue;
            }
            if (contiguous) {
                chargeable++;
            }
            if (i < durationMinutes) {
                sum = sum.add(tariff.get().getPricePerHour().divide(SIXTY, MC));
            }
        }
        boolean full = chargeable >= durationMinutes;
        BigDecimal amount = full ? sum.setScale(2, RoundingMode.HALF_UP) : null;
        return new Price(amount, rateAtStart, Math.min(chargeable, limit), chargingEndsAt);
    }
}
