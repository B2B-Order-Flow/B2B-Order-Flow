package com.b2borderflow.core.sla;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Календарь, по которому считается SLA (docs/system/sla.md, раздел 3).
 * Профиль {@code PROD} работает по рабочим часам и праздникам, {@code DEMO} —
 * по настенным минутам круглосуточно. Функции расчёта времени получают календарь
 * параметром, поэтому подмена профиля не меняет алгоритм.
 */
public interface SlaCalendar {

    ZoneId zone();

    LocalTime dayStart();

    /** Длительность рабочего дня; для {@code DEMO} — сутки. */
    Duration workingDayDuration();

    boolean isWorkingDay(LocalDate date);
}
