package com.b2borderflow.core.sla;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Календарный (настенный) календарь: время идёт круглосуточно, без рабочих часов
 * и праздников. Нужен нормативам {@code calendar_kind = CALENDAR} в профиле
 * {@code PROD} — например, контрольному сроку ТВ и сроку стройки
 * (docs/system/sla.md, 3.1). Это не то же самое, что {@link ProdSlaCalendar}:
 * такой норматив считается календарными минутами, а не рабочими.
 */
public final class WallClockSlaCalendar implements SlaCalendar {

    public static final WallClockSlaCalendar INSTANCE = new WallClockSlaCalendar();

    private static final Duration DAY_DURATION = Duration.ofDays(1);

    private WallClockSlaCalendar() {
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
