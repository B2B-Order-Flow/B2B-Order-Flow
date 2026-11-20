package com.b2borderflow.core.sla;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Календарь профиля {@code PROD}: понедельник–пятница, 09:00–17:00 по
 * {@code Europe/Moscow}, праздники и переносы — из {@code work_calendar}
 * (docs/system/sla.md, 3.1). Обед из норматива не вычитается.
 *
 * <p>Для дат вне периода, заполненного миграцией, действует правило «рабочий день —
 * понедельник–пятница»: так календарь остаётся рабочим и после января 2027.</p>
 */
public final class ProdSlaCalendar implements SlaCalendar {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Moscow");
    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final Duration DAY_DURATION = Duration.ofHours(8);

    private final ZoneId zone;
    private final Map<LocalDate, Boolean> workingDays;

    public ProdSlaCalendar(ZoneId zone, Map<LocalDate, Boolean> workingDays) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.workingDays = Map.copyOf(Objects.requireNonNull(workingDays, "workingDays"));
    }

    /**
     * Собирает календарь из строк {@code work_calendar}. Часовой пояс берётся из
     * первой строки (он одинаков для всего календаря), по умолчанию — {@code Europe/Moscow}.
     */
    public static ProdSlaCalendar fromEntries(List<WorkCalendar> entries) {
        Objects.requireNonNull(entries, "entries");
        ZoneId zone = entries.stream()
                .map(WorkCalendar::getTimezone)
                .filter(Objects::nonNull)
                .findFirst()
                .map(ZoneId::of)
                .orElse(DEFAULT_ZONE);
        Map<LocalDate, Boolean> workingDays = entries.stream()
                .collect(Collectors.toMap(
                        WorkCalendar::getCalendarDate,
                        WorkCalendar::isWorking,
                        (first, second) -> second));
        return new ProdSlaCalendar(zone, workingDays);
    }

    @Override
    public ZoneId zone() {
        return zone;
    }

    @Override
    public LocalTime dayStart() {
        return DAY_START;
    }

    @Override
    public Duration workingDayDuration() {
        return DAY_DURATION;
    }

    @Override
    public boolean isWorkingDay(LocalDate date) {
        Boolean explicit = workingDays.get(date);
        if (explicit != null) {
            return explicit;
        }
        return date.getDayOfWeek().getValue() <= 5;
    }
}
