package com.sep.parking.common.config;

import java.time.ZoneId;

/** Pravila naplate (radno vreme, dani) vaze u lokalnom vremenu grada. */
public final class ParkingTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Belgrade");

    private ParkingTime() {
    }
}
