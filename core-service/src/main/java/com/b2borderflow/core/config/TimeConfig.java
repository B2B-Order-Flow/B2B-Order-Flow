package com.b2borderflow.core.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Источник времени ядра. В тестах подменяется фиксированным {@link Clock},
 * чтобы SLA и аудит не зависели от реальных часов.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
