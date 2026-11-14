package com.sep.parking.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Sat kao bean - u testovima se zamenjuje fiksnim vremenom. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
