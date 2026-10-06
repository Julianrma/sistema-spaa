package com.spa.sistema_spa;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReservationTimeConfiguration {
    @Bean
    Clock reservationClock() {
        return Clock.system(ReservationPolicy.ZONE);
    }
}
