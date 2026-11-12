package com.sep.cardplugin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Plugin za placanje platnom karticom. Implementira isti ugovor kao ostali plugin-ovi PSP-a
 * ({@code POST /payments}) i posreduje izmedju PSP Core-a i banke prodavca (Acquirer).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CardPluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardPluginApplication.class, args);
    }
}
