package com.b2borderflow.core.sla;

import com.b2borderflow.core.config.SlaProperties;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Выбирает календарь для норматива. Профиль {@code DEMO} считает всё настенными
 * минутами; в {@code PROD} решает {@link SlaNorm#getCalendarKind()}:
 * {@code WORK} — рабочие часы {@link ProdSlaCalendar}, {@code CALENDAR} —
 * календарные минуты {@link WallClockSlaCalendar}. Только по профилю выбирать
 * календарь нельзя: у контрольного срока ТВ и стройки в {@code PROD} норматив
 * задан календарными минутами (docs/system/sla.md, 3.1).
 */
@Component
public class SlaCalendarFactory {

    private final SlaProperties properties;
    private final WorkCalendarRepository workCalendarRepository;

    public SlaCalendarFactory(SlaProperties properties, WorkCalendarRepository workCalendarRepository) {
        this.properties = properties;
        this.workCalendarRepository = workCalendarRepository;
    }

    public SlaCalendar forNorm(SlaNorm norm) {
        if (norm == null) {
            throw new IllegalArgumentException("Для расчёта SLA нужен норматив");
        }
        if (properties.getProfile() == SlaProfile.DEMO) {
            return DemoSlaCalendar.INSTANCE;
        }
        if (norm.getCalendarKind() == SlaCalendarKind.CALENDAR) {
            return WallClockSlaCalendar.INSTANCE;
        }
        return prodCalendar();
    }

    public SlaCalendar forProfile() {
        return properties.getProfile() == SlaProfile.DEMO
                ? DemoSlaCalendar.INSTANCE
                : prodCalendar();
    }

    /** Часовой пояс бизнес-дат: «сегодня» считается по календарю оператора. */
    public ZoneId businessZone() {
        return properties.getProfile() == SlaProfile.DEMO
                ? DemoSlaCalendar.INSTANCE.zone()
                : ProdSlaCalendar.DEFAULT_ZONE;
    }

    private SlaCalendar prodCalendar() {
        return ProdSlaCalendar.fromEntries(workCalendarRepository.findAll());
    }
}
