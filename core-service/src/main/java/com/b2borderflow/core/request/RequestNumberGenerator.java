package com.b2borderflow.core.request;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Генерация человекочитаемого номера {@code REQ-ГГГГ-NNNNNN} из последовательности БД.
 * Последовательность общая, номер уникален.
 */
@Component
public class RequestNumberGenerator {

    private final EntityManager entityManager;
    private final Clock clock;

    public RequestNumberGenerator(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public String next() {
        Number sequence = (Number) entityManager
                .createNativeQuery("select nextval('request_number_seq')")
                .getSingleResult();
        int year = LocalDate.now(clock.withZone(ZoneOffset.UTC)).getYear();
        return "REQ-%d-%06d".formatted(year, sequence.longValue());
    }
}
