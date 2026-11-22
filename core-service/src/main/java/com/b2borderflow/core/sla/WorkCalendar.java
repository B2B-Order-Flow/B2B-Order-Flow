package com.b2borderflow.core.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * День производственного календаря {@code work_calendar} (docs/system/domain-model.md, 4.8).
 * Заполняется миграцией на период разработки и защиты; {@code is_working = false} в будний
 * день означает праздник, {@code true} в выходной — перенос.
 */
@Entity
@Table(name = "work_calendar")
public class WorkCalendar {

    @Id
    @Column(name = "calendar_date", nullable = false, updatable = false)
    private LocalDate calendarDate;

    @Column(name = "is_working", nullable = false)
    private boolean working;

    @Column(name = "note")
    private String note;

    @Column(name = "timezone", nullable = false)
    private String timezone = "Europe/Moscow";

    public LocalDate getCalendarDate() {
        return calendarDate;
    }

    public void setCalendarDate(LocalDate calendarDate) {
        this.calendarDate = calendarDate;
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        this.working = working;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
