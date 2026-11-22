package com.b2borderflow.core.sla;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlaTimerRepository extends JpaRepository<SlaTimer, UUID> {

    List<SlaTimer> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    Optional<SlaTimer> findFirstByStageIdAndStatusIn(UUID stageId, Collection<SlaTimerStatus> statuses);

    Optional<SlaTimer> findFirstByRequestIdAndKindAndStatusIn(
            UUID requestId, SlaTimerKind kind, Collection<SlaTimerStatus> statuses);

    Optional<SlaTimer> findFirstByRequestIdAndKindOrderByCreatedAtDesc(
            UUID requestId, SlaTimerKind kind);

    List<SlaTimer> findByRequestIdAndStatusIn(UUID requestId, Collection<SlaTimerStatus> statuses);

    /** Кандидаты планировщика; блокировка {@code FOR UPDATE SKIP LOCKED} — в {@link #lockDue}. */
    List<SlaTimer> findByStatusInAndNextEventAtLessThanEqualOrderByNextEventAtAsc(
            Collection<SlaTimerStatus> statuses, OffsetDateTime moment, Pageable pageable);

    /**
     * Таймеры, у которых наступило событие, с блокировкой строки на время транзакции.
     * {@code SKIP LOCKED} не даёт двум экземплярам ядра обработать один таймер дважды.
     */
    @Query(value = """
            select * from sla_timer
            where status in ('RUNNING', 'PAUSED')
              and next_event_at is not null
              and next_event_at <= :moment
            order by next_event_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<SlaTimer> lockDue(@Param("moment") OffsetDateTime moment, @Param("batchSize") int batchSize);
}
