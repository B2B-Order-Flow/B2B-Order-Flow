package com.b2borderflow.core.sla;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPauseRepository extends JpaRepository<SlaPause, UUID> {

    List<SlaPause> findByTimerIdOrderByStartedAtAsc(UUID timerId);

    Optional<SlaPause> findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(UUID timerId);
}
