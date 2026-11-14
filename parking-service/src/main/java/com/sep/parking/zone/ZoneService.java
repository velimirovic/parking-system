package com.sep.parking.zone;

import com.sep.parking.common.config.ParkingTime;
import com.sep.parking.common.exception.ApiException;
import com.sep.parking.zone.ZoneDtos.LocationResponse;
import com.sep.parking.zone.ZoneDtos.TariffResponse;
import com.sep.parking.zone.ZoneDtos.ZoneResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZoneService {

    private final ParkingZoneRepository zones;
    private final ParkingLocationRepository locations;
    private final Clock clock;

    public ZoneService(ParkingZoneRepository zones, ParkingLocationRepository locations, Clock clock) {
        this.zones = zones;
        this.locations = locations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> list() {
        Map<Long, List<ParkingLocation>> byZone = locations.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(l -> l.getZone().getId()));
        return zones.findAllOrdered().stream()
                .map(z -> toResponse(z, byZone.getOrDefault(z.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse get(Long id) {
        ParkingZone zone = getEntity(id);
        return toResponse(zone, locations.findByZoneIdOrderByNameAsc(id));
    }

    @Transactional(readOnly = true)
    public ParkingZone getEntity(Long id) {
        return zones.findWithTariffs(id)
                .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "Zona nije pronađena."));
    }

    private ZoneResponse toResponse(ParkingZone z, List<ParkingLocation> zoneLocations) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ParkingTime.ZONE);
        BigDecimal current = z.tariffAt(now).map(ZoneTariff::getPricePerHour).orElse(null);
        BigDecimal base = z.getTariffs().stream().map(ZoneTariff::getPricePerHour)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return new ZoneResponse(z.getId(), z.getCode(), z.getName(), z.getArea(), z.getDescription(),
                z.getLatitude(), z.getLongitude(), z.getCurrency(), z.getMinDurationMinutes(),
                z.getMaxDurationMinutes(), z.getDurationStepMinutes(), z.isExtensionAllowed(),
                z.getMaxExtensions(), z.getMaxTotalMinutes(), z.isActive(), base, current != null, current,
                z.getTariffs().stream().map(TariffResponse::of).toList(),
                zoneLocations.stream().map(LocationResponse::of).toList());
    }
}
