package com.sep.parking.session;

import com.sep.parking.common.config.ParkingTime;
import com.sep.parking.session.SessionDtos.CheckState;
import com.sep.parking.session.SessionDtos.DurationOption;
import com.sep.parking.session.SessionDtos.QuoteResponse;
import com.sep.parking.session.SessionDtos.RuleCheck;
import com.sep.parking.vehicle.Vehicle;
import com.sep.parking.zone.ParkingZone;
import com.sep.parking.zone.ZonePricing;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Provera pravila zone pre pokretanja sesije i obracun cene. Isti kod koriste i obracun za
 * prikaz (quote) i kreiranje naloga, pa iznos koji korisnik vidi odgovara iznosu koji se salje PSP-u.
 */
@Component
public class SessionRules {

    /** Ponudjena trajanja (filtriraju se po pravilima zone). */
    private static final int[] STANDARD_DURATIONS = {15, 30, 45, 60, 90, 120, 180, 240, 300, 360};
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final ZonePricing pricing;
    private final ParkingSessionRepository sessions;

    public SessionRules(ZonePricing pricing, ParkingSessionRepository sessions) {
        this.pricing = pricing;
        this.sessions = sessions;
    }

    public QuoteResponse evaluate(ParkingZone zone, Vehicle vehicle, Long locationId, int durationMinutes,
                                  Instant now) {
        Instant start = now.truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(durationMinutes, ChronoUnit.MINUTES);
        ZonePricing.Price price = pricing.price(zone, start, durationMinutes, zone.getMaxDurationMinutes());
        int maxAvailable = zone.isActive()
                ? (price.chargeableMinutes() / zone.getDurationStepMinutes()) * zone.getDurationStepMinutes()
                : 0;

        List<RuleCheck> checks = new ArrayList<>();
        checks.add(zone.isActive()
                ? new RuleCheck("ZONE_ACTIVE", "Zona je aktivna", CheckState.OK, null)
                : new RuleCheck("ZONE_ACTIVE", "Zona je aktivna", CheckState.FAIL,
                        "Parkiranje u ovoj zoni trenutno nije moguće."));

        String chargingLabel = "U okviru radnog vremena naplate";
        if (price.rateAtStart() == null) {
            checks.add(new RuleCheck("CHARGING_HOURS", chargingLabel, CheckState.FAIL,
                    "U ovoj zoni trenutno nema naplate — parkiranje je besplatno van radnog vremena."));
        } else if (!price.fullyCharged(durationMinutes)) {
            String until = price.chargingEndsAt() == null ? ""
                    : " u " + HHMM.format(price.chargingEndsAt().atZone(ParkingTime.ZONE));
            checks.add(new RuleCheck("CHARGING_HOURS", chargingLabel, CheckState.FAIL,
                    "Naplata se završava" + until + " — najduže možete platiti " + maxAvailable + " min."));
        } else {
            checks.add(new RuleCheck("CHARGING_HOURS", chargingLabel, CheckState.OK, null));
        }

        boolean durationOk = durationMinutes >= zone.getMinDurationMinutes()
                && durationMinutes <= zone.getMaxDurationMinutes()
                && durationMinutes % zone.getDurationStepMinutes() == 0;
        checks.add(durationOk
                ? new RuleCheck("DURATION_LIMITS", "Trajanje u granicama zone", CheckState.OK, null)
                : new RuleCheck("DURATION_LIMITS", "Trajanje u granicama zone", CheckState.FAIL,
                        "Trajanje mora biti između " + zone.getMinDurationMinutes() + " i "
                                + zone.getMaxDurationMinutes() + " min, u koracima od "
                                + zone.getDurationStepMinutes() + " min."));

        if (vehicle == null) {
            checks.add(new RuleCheck("VEHICLE_FREE", "Vozilo nema aktivnu sesiju", CheckState.PENDING,
                    "Izaberite vozilo."));
        } else if (sessions.hasOpenSession(vehicle.getId(), now)) {
            checks.add(new RuleCheck("VEHICLE_FREE", "Vozilo nema aktivnu sesiju", CheckState.FAIL,
                    "Vozilo " + vehicle.getDisplayPlate()
                            + " već ima aktivnu parking sesiju ili plaćanje u toku."));
        } else {
            checks.add(new RuleCheck("VEHICLE_FREE", "Vozilo nema aktivnu sesiju", CheckState.OK, null));
        }

        boolean valid = checks.stream().allMatch(c -> c.state() == CheckState.OK);
        List<DurationOption> durations = new ArrayList<>();
        for (int d : STANDARD_DURATIONS) {
            if (d >= zone.getMinDurationMinutes() && d <= zone.getMaxDurationMinutes()
                    && d % zone.getDurationStepMinutes() == 0) {
                durations.add(new DurationOption(d, d <= maxAvailable));
            }
        }

        return new QuoteResponse(zone.getId(), zone.getName(), vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getDisplayPlate(), locationId, durationMinutes, start, end,
                price.rateAtStart(), price.amount(), zone.getCurrency(), valid, maxAvailable,
                price.rateAtStart() != null, price.chargingEndsAt(), checks, durations);
    }

    /** Prvo pravilo koje nije ispunjeno (poruka za korisnika). */
    public static RuleCheck firstFailure(QuoteResponse quote) {
        return quote.checks().stream().filter(c -> c.state() != CheckState.OK).findFirst().orElse(null);
    }
}
