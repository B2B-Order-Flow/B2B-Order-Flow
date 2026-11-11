package com.b2borderflow.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Проверка, что Flyway и {@code ddl-auto=validate} сходятся с сущностями. */
class ContextLoadsTest extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndSchemaValidates() {
        assertThat(companyRepository.count()).isZero();
    }
}
