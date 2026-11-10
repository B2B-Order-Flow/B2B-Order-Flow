package com.b2borderflow.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа транзакционного ядра.
 *
 * <p>Ядро — единственный источник истины по статусам, правам и данным заявок.
 * Контракт API генерируется из {@code src/main/resources/openapi/core-api.yaml},
 * поэтому реализации контроллеров лежат под {@link com.b2borderflow.core.api}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRetry
@EnableScheduling
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
