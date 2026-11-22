package com.b2borderflow.core.sla;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPauseRepository extends JpaRepository<SlaPause, UUID> {

    List<SlaPause> findByTimerIdOrderByStartedAtAsc(UUID timerId);

    List<SlaPause> findByTimerIdInOrderByStartedAtDesc(Collection<UUID> timerIds);

    Optional<SlaPause> findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(UUID timerId);

    Optional<SlaPause> findFirstByTimerIdAndEndedAtIsNotNullOrderByEndedAtDesc(UUID timerId);
}
