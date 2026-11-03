package com.sep.psp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PspServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PspServiceApplication.class, args);
	}

}
