package com.DataLaburo.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ApplicationClockConfig {
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }
}
