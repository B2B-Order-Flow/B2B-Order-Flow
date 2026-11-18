package com.b2borderflow.core.sla;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaTimerRepository extends JpaRepository<SlaTimer, UUID> {

    List<SlaTimer> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    Optional<SlaTimer> findFirstByStageIdAndStatusIn(UUID stageId, Collection<SlaTimerStatus> statuses);

    Optional<SlaTimer> findFirstByRequestIdAndKindAndStatusIn(
            UUID requestId, SlaTimerKind kind, Collection<SlaTimerStatus> statuses);

    /** Кандидаты планировщика; блокировка {@code FOR UPDATE SKIP LOCKED} — на неделе 5. */
    List<SlaTimer> findByStatusInAndNextEventAtLessThanEqualOrderByNextEventAtAsc(
            Collection<SlaTimerStatus> statuses, OffsetDateTime moment, Pageable pageable);
}
