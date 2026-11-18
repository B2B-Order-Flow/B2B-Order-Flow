package com.b2borderflow.core.sla;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, LocalDate> {

    Optional<WorkCalendar> findByCalendarDate(LocalDate calendarDate);

    List<WorkCalendar> findByCalendarDateBetweenOrderByCalendarDateAsc(LocalDate from, LocalDate to);
}
