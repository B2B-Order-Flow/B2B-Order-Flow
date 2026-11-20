package com.b2borderflow.core.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Расчёт рабочего времени SLA на фиксированном {@link Clock}
 * (docs/system/sla.md, 3, 5.2 и 11).
 */
class SlaTimeTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneOffset.UTC);

    /** 16 ноября 2026 — понедельник; 4 ноября — праздник, 7–8 ноября — выходные. */
    private static final SlaCalendar PROD = new ProdSlaCalendar(
            ProdSlaCalendar.DEFAULT_ZONE,
            Map.of(LocalDate.of(2026, 11, 4), false));

    @Test
    void clockIsFixedAtMondayMorning() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);

        assertThat(now).isEqualTo(at("2026-11-16T06:00:00Z"));
        assertThat(SlaTime.addWorkingTime(now, Duration.ofHours(9), PROD))
                .as("9 рабочих часов с утра понедельника заканчиваются во вторник в 10:00 МСК")
                .isEqualTo(at("2026-11-17T07:00:00Z"));
    }

    @Test
    void addsWorkingTimeWithinOneDay() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-16T06:00:00Z"), Duration.ofHours(8), PROD))
                .isEqualTo(at("2026-11-16T14:00:00Z"));
    }

    @Test
    void movesRemainingTimeToNextWorkingDay() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-16T06:00:00Z"), Duration.ofHours(9), PROD))
                .isEqualTo(at("2026-11-17T07:00:00Z"));
    }

    @Test
    void startsFromWorkingDayBoundaryWhenCalledBeforeOpening() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-16T05:00:00Z"), Duration.ofHours(1), PROD))
                .isEqualTo(at("2026-11-16T07:00:00Z"));
    }

    @Test
    void movesFromAfterHoursToNextMorning() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-16T15:00:00Z"), Duration.ofHours(1), PROD))
                .isEqualTo(at("2026-11-17T07:00:00Z"));
    }

    @Test
    void skipsWeekend() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-06T13:00:00Z"), Duration.ofHours(2), PROD))
                .isEqualTo(at("2026-11-09T07:00:00Z"));
    }

    @Test
    void skipsHoliday() {
        assertThat(SlaTime.addWorkingTime(at("2026-11-03T13:00:00Z"), Duration.ofHours(2), PROD))
                .isEqualTo(at("2026-11-05T07:00:00Z"));
    }

    @Test
    void zeroDurationReturnsSameMoment() {
        OffsetDateTime from = at("2026-11-16T06:00:00Z");

        assertThat(SlaTime.addWorkingTime(from, Duration.ZERO, PROD)).isEqualTo(from);
    }

    @Test
    void negativeDurationIsRejected() {
        assertThatThrownBy(() -> SlaTime.addWorkingTime(
                at("2026-11-16T06:00:00Z"), Duration.ofMinutes(-1), PROD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workingTimeBetweenWithinOneDay() {
        assertThat(SlaTime.workingTimeBetween(
                at("2026-11-16T06:00:00Z"), at("2026-11-16T14:00:00Z"), PROD))
                .isEqualTo(Duration.ofHours(8));
    }

    @Test
    void workingTimeBetweenSkipsWeekend() {
        assertThat(SlaTime.workingTimeBetween(
                at("2026-11-06T13:00:00Z"), at("2026-11-09T07:00:00Z"), PROD))
                .as("пятница 16:00–17:00 и понедельник 09:00–10:00")
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void workingTimeBetweenSkipsHoliday() {
        assertThat(SlaTime.workingTimeBetween(
                at("2026-11-03T13:00:00Z"), at("2026-11-05T13:00:00Z"), PROD))
                .isEqualTo(Duration.ofHours(8));
    }

    @Test
    void workingTimeBetweenReverseOrderIsZero() {
        assertThat(SlaTime.workingTimeBetween(
                at("2026-11-16T14:00:00Z"), at("2026-11-16T06:00:00Z"), PROD))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void addAndMeasureAreConsistent() {
        OffsetDateTime from = at("2026-11-16T06:00:00Z");
        Duration duration = Duration.ofHours(10);

        OffsetDateTime to = SlaTime.addWorkingTime(from, duration, PROD);

        assertThat(SlaTime.workingTimeBetween(from, to, PROD)).isEqualTo(duration);
    }

    @Test
    void demoProfileCountsWallClockMinutes() {
        OffsetDateTime from = at("2026-11-16T23:30:00Z");

        assertThat(SlaTime.addWorkingTime(from, Duration.ofMinutes(90), DemoSlaCalendar.INSTANCE))
                .isEqualTo(at("2026-11-17T01:00:00Z"));
        assertThat(SlaTime.workingTimeBetween(from, at("2026-11-17T01:00:00Z"), DemoSlaCalendar.INSTANCE))
                .isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void prodCalendarIsBuiltFromWorkCalendarEntries() {
        WorkCalendar holiday = new WorkCalendar();
        holiday.setCalendarDate(LocalDate.of(2026, 11, 4));
        holiday.setWorking(false);
        holiday.setNote("День народного единства");
        holiday.setTimezone("Europe/Moscow");

        SlaCalendar calendar = ProdSlaCalendar.fromEntries(List.of(holiday));

        assertThat(SlaTime.addWorkingTime(at("2026-11-03T13:00:00Z"), Duration.ofHours(2), calendar))
                .isEqualTo(at("2026-11-05T07:00:00Z"));
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }
}
