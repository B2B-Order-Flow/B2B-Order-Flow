package com.b2borderflow.core.sla;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Расчёт рабочего времени SLA (docs/system/sla.md, 5.2).
 *
 * <p>Две функции — прямые и обратные: {@link #addWorkingTime} находит момент
 * «точка во времени + рабочий срок», {@link #workingTimeBetween} — сколько рабочего
 * времени прошло между двумя моментами. Обе работают в часовом поясе календаря и
 * возвращают результат в исходном смещении, чтобы вызывающий код не терял UTC.</p>
 */
public final class SlaTime {

    /** Защита от бесконечного цикла при бессмысленно большом сроке. */
    private static final int MAX_DAYS = 10_000;

    private SlaTime() {
    }

    /**
     * Прибавляет рабочее время к моменту {@code from}. Паузы функцией не учитываются:
     * их вычитает таймер SLA, пересчитывая {@code due_at} при снятии паузы.
     */
    public static OffsetDateTime addWorkingTime(OffsetDateTime from, Duration duration, SlaCalendar calendar) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(calendar, "calendar");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Рабочий срок не может быть отрицательным: " + duration);
        }
        if (duration.isZero()) {
            return from;
        }

        ZoneId zone = calendar.zone();
        ZonedDateTime cursor = from.atZoneSameInstant(zone);
        Duration remaining = duration;

        for (int day = 0; day <= MAX_DAYS; day++) {
            LocalDate date = cursor.toLocalDate();
            if (calendar.isWorkingDay(date)) {
                ZonedDateTime start = date.atTime(calendar.dayStart()).atZone(zone);
                ZonedDateTime end = start.plus(calendar.workingDayDuration());
                if (cursor.isBefore(start)) {
                    cursor = start;
                }
                if (cursor.isBefore(end)) {
                    Duration available = Duration.between(cursor, end);
                    if (remaining.compareTo(available) <= 0) {
                        return cursor.plus(remaining)
                                .withZoneSameInstant(from.getOffset())
                                .toOffsetDateTime();
                    }
                    remaining = remaining.minus(available);
                }
            }
            cursor = date.plusDays(1).atStartOfDay(zone);
        }
        throw new IllegalStateException("Срок " + duration + " не укладывается в " + MAX_DAYS + " дней");
    }

    /**
     * Считает рабочее время между двумя моментами. Если {@code to} не позже {@code from},
     * результат — ноль: обратный порядок не превращается в отрицательный срок.
     */
    public static Duration workingTimeBetween(OffsetDateTime from, OffsetDateTime to, SlaCalendar calendar) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(calendar, "calendar");
        if (!to.isAfter(from)) {
            return Duration.ZERO;
        }

        ZoneId zone = calendar.zone();
        ZonedDateTime startCursor = from.atZoneSameInstant(zone);
        ZonedDateTime endCursor = to.atZoneSameInstant(zone);
        LocalDate lastDate = endCursor.toLocalDate();
        Duration total = Duration.ZERO;

        LocalDate date = startCursor.toLocalDate();
        for (int day = 0; !date.isAfter(lastDate); day++) {
            if (day > MAX_DAYS) {
                throw new IllegalStateException(
                        "Интервал " + Duration.between(from, to) + " не укладывается в " + MAX_DAYS + " дней");
            }
            if (calendar.isWorkingDay(date)) {
                ZonedDateTime start = date.atTime(calendar.dayStart()).atZone(zone);
                ZonedDateTime end = start.plus(calendar.workingDayDuration());
                ZonedDateTime overlapStart = startCursor.isAfter(start) ? startCursor : start;
                ZonedDateTime overlapEnd = endCursor.isBefore(end) ? endCursor : end;
                if (overlapStart.isBefore(overlapEnd)) {
                    total = total.plus(Duration.between(overlapStart, overlapEnd));
                }
            }
            date = date.plusDays(1);
        }
        return total;
    }
}
