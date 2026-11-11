package com.b2borderflow.core.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Контрольная сумма ИНН проверяется локально до вызова справочника (EC-02). */
class InnValidatorTest {

    private final InnValidator validator = new InnValidator();

    @ParameterizedTest
    @CsvSource({
            "7725012341, true",
            "7844002230, true",
            "7719003349, true",
            "5050123455, true",
            "770112345127, true",
            "7725012340, false",
            "770112345120, false",
            "1234567890, false",
            "772501234, false",
            "77250123411, false"
    })
    void validatesInnChecksumAndLength(String inn, boolean expected) {
        assertThat(validator.isValid(inn)).isEqualTo(expected);
    }

    @Test
    void nullAndBlankAreInvalid() {
        assertThat(validator.isValid(null)).isFalse();
        assertThat(validator.isValid("  ")).isFalse();
        assertThat(validator.isValid("7725O12341")).isFalse();
    }
}
