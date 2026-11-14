package com.sep.parking.zone;

import java.math.BigDecimal;
import java.util.List;

public final class ZoneDtos {

    private ZoneDtos() {
    }

    public record TariffResponse(String label, int dayFrom, int dayTo, String from, String to,
                                 BigDecimal pricePerHour) {

        static TariffResponse of(ZoneTariff t) {
            return new TariffResponse(t.getLabel(), t.getDayFrom(), t.getDayTo(), hhmm(t.getStartMinute()),
                    hhmm(t.getEndMinute()), t.getPricePerHour());
        }

        private static String hhmm(int minute) {
            return String.format("%02d:%02d", minute / 60, minute % 60);
        }
    }

    public record LocationResponse(Long id, String name, String address, Integer capacity) {

        static LocationResponse of(ParkingLocation l) {
            return new LocationResponse(l.getId(), l.getName(), l.getAddress(), l.getCapacity());
        }
    }

    /**
     * @param baseRatePerHour najvisa cena po satu u zoni (za listu zona)
     * @param chargingNow     da li se u ovom trenutku naplacuje parking
     * @param currentRate     cena po satu koja trenutno vazi (null kada nema naplate)
     */
    public record ZoneResponse(Long id, String code, String name, String area, String description,
                               BigDecimal latitude, BigDecimal longitude, String currency,
                               int minDurationMinutes, int maxDurationMinutes, int durationStepMinutes,
                               boolean extensionAllowed, Integer maxExtensions, Integer maxTotalMinutes,
                               boolean active, BigDecimal baseRatePerHour, boolean chargingNow,
                               BigDecimal currentRate, List<TariffResponse> tariffs,
                               List<LocationResponse> locations) {
    }
}
