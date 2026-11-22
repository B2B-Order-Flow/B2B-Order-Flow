package com.b2borderflow.core.sla;

/**
 * Признак календаря норматива {@code sla_norm.calendar_kind}: {@code WORK} — рабочие
 * минуты по календарю {@code PROD}, {@code CALENDAR} — календарные минуты
 * (docs/system/sla.md, 3.1).
 */
public enum SlaCalendarKind {

    WORK,
    CALENDAR
}
