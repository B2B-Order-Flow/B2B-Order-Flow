package com.b2borderflow.core.sla;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Календарь профиля {@code DEMO}: календаря нет, время идёт круглосуточно по
 * настенным часам (docs/system/sla.md, 3.2). Все минуты — календарные.
 */
public final class DemoSlaCalendar implements SlaCalendar {

    public static final DemoSlaCalendar INSTANCE = new DemoSlaCalendar();

    private static final Duration DAY_DURATION = Duration.ofDays(1);

    private DemoSlaCalendar() {
    }

    @Override
    public ZoneId zone() {
        return ZoneOffset.UTC;
    }

    @Override
    public LocalTime dayStart() {
        return LocalTime.MIDNIGHT;
    }

    @Override
    public Duration workingDayDuration() {
        return DAY_DURATION;
    }

    @Override
    public boolean isWorkingDay(LocalDate date) {
        return true;
    }
}
