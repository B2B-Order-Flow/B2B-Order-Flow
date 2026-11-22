package com.b2borderflow.core.sla;

import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.config.SlaProperties;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.notification.NotificationEvent;
import com.b2borderflow.core.notification.NotificationService;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestRepository;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.request.RequestStageRepository;
import com.b2borderflow.core.request.RequestStageType;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обработка наступивших событий SLA: предупреждение, нарушение, эскалация,
 * предупреждение и предел паузы, контрольные сроки заявки
 * (docs/system/sla.md, разделы 5–9).
 *
 * <p>Планировщик вызывает {@link #processDueTimers()}; выборка идёт с
 * {@code FOR UPDATE SKIP LOCKED}, а идемпотентность обеспечивают
 * {@code warned_at}, {@code breached_at}, {@code escalation_level} и
 * {@code next_event_at}. Уведомление пишется в той же транзакции, что и
 * изменение таймера.</p>
 */
@Service
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

    private final SlaTimerRepository timerRepository;
    private final SlaPauseRepository pauseRepository;
    private final RequestRepository requestRepository;
    private final RequestStageRepository stageRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SlaProperties properties;
    private final Clock clock;

    public SlaMonitor(SlaTimerRepository timerRepository,
                      SlaPauseRepository pauseRepository,
                      RequestRepository requestRepository,
                      RequestStageRepository stageRepository,
                      NotificationService notificationService,
                      AuditService auditService,
                      SlaProperties properties,
                      Clock clock) {
        this.timerRepository = timerRepository;
        this.pauseRepository = pauseRepository;
        this.requestRepository = requestRepository;
        this.stageRepository = stageRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Один проход планировщика. Возвращает число обработанных таймеров. */
    @Transactional
    public int processDueTimers() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SlaTimer> due = timerRepository.lockDue(now, properties.getSchedulerBatchSize());
        int processed = 0;
        for (SlaTimer timer : due) {
            if (timer.getStatus() == SlaTimerStatus.PAUSED) {
                processPause(timer, now);
            } else if (timer.getStatus() == SlaTimerStatus.RUNNING) {
                processRunning(timer, now);
            } else {
                continue;
            }
            processed++;
        }
        if (processed > 0) {
            log.info("SLA Monitor обработал {} таймеров", processed);
        }
        return processed;
    }

    private void processRunning(SlaTimer timer, OffsetDateTime now) {
        Optional<Request> requestOptional = requestRepository.findByIdAndDeletedAtIsNull(timer.getRequestId());
        if (requestOptional.isEmpty()) {
            timer.setNextEventAt(null);
            timerRepository.save(timer);
            return;
        }
        Request request = requestOptional.get();
        RequestStage stage = timer.getStageId() == null
                ? null
                : stageRepository.findById(timer.getStageId()).orElse(null);
        boolean stageTimer = timer.getKind() == SlaTimerKind.STAGE_SLA && stage != null;

        if (timer.getWarnedAt() == null && due(timer.getWarnAt(), now)) {
            warn(timer, request, stage, now);
        }
        if (timer.getBreachedAt() == null && due(timer.getDueAt(), now)) {
            breach(timer, request, stage, now);
        }
        if (stageTimer && timer.getEscalationLevel() < 2 && due(timer.getEscalateAt(), now)) {
            escalate(timer, request, stage);
        }
        timer.setNextEventAt(nextEvent(timer, stageTimer, now));
        timerRepository.save(timer);
    }

    private void processPause(SlaTimer timer, OffsetDateTime now) {
        Optional<SlaPause> pauseOptional =
                pauseRepository.findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(timer.getId());
        Optional<Request> requestOptional = requestRepository.findByIdAndDeletedAtIsNull(timer.getRequestId());
        if (pauseOptional.isEmpty() || requestOptional.isEmpty()) {
            timer.setNextEventAt(null);
            timerRepository.save(timer);
            return;
        }
        SlaPause pause = pauseOptional.get();
        Request request = requestOptional.get();
        RequestStage stage = timer.getStageId() == null
                ? null
                : stageRepository.findById(timer.getStageId()).orElse(null);

        // Предел паузы по акту — это срок приёмки; им управляет контрольный ACCEPTANCE.
        if (stage != null && stage.getType() == RequestStageType.CLOSING
                && pause.getReasonCode() == PauseReasonCode.AWAIT_CLIENT_SIGNATURE) {
            timer.setNextEventAt(null);
            timerRepository.save(timer);
            return;
        }

        OffsetDateTime limit = pause.getLimitAt();
        if (limit == null) {
            timer.setNextEventAt(null);
            timerRepository.save(timer);
            return;
        }

        if (now.isBefore(limit)) {
            if (due(timer.getNextEventAt(), now)) {
                notificationService.notifyCoordinatorsAndManager(request,
                        NotificationEvent.PAUSE_LIMIT_WARNING, pausePayload(timer, stage, pause));
                audit(timer, request, stage, AuditAction.PAUSE_LIMIT_WARNING_SENT,
                        Map.of("pauseLimitAt", limit.toString()));
                timer.setNextEventAt(limit);
            }
        } else {
            notificationService.notifyCoordinatorsAndManager(request,
                    NotificationEvent.PAUSE_LIMIT_REACHED, pausePayload(timer, stage, pause));
            audit(timer, request, stage, AuditAction.PAUSE_LIMIT_REACHED,
                    Map.of("pauseLimitAt", limit.toString()));
            timer.setNextEventAt(null);
        }
        timerRepository.save(timer);
    }

    private void warn(SlaTimer timer, Request request, RequestStage stage, OffsetDateTime now) {
        timer.setWarnedAt(now);
        Map<String, Object> payload = timerPayload(timer, stage);
        if (timer.getKind() == SlaTimerKind.STAGE_SLA && stage != null) {
            payload.put("minutesLeft", minutesLeft(timer.getDueAt(), now));
            if (stage.getAssigneeId() != null) {
                notificationService.notifyAssignee(stage, request, NotificationEvent.SLA_WARNING, payload);
            } else {
                notificationService.notifyQueue(stage, request, NotificationEvent.SLA_WARNING, payload);
                notificationService.notifyCoordinators(request, NotificationEvent.SLA_WARNING, payload);
            }
            audit(timer, request, stage, AuditAction.SLA_WARNING_SENT, payload);
            return;
        }
        if (timer.getKind() == SlaTimerKind.ACCEPTANCE) {
            notificationService.notifyAuthor(request, NotificationEvent.ACCEPTANCE_REMINDER, payload);
            audit(timer, request, null, AuditAction.CONTROL_DEADLINE_WARNING_SENT, payload);
        } else {
            notificationService.notifyCoordinators(request,
                    NotificationEvent.CONTROL_DEADLINE_WARNING, payload);
            audit(timer, request, null, AuditAction.CONTROL_DEADLINE_WARNING_SENT, payload);
        }
    }

    private void breach(SlaTimer timer, Request request, RequestStage stage, OffsetDateTime now) {
        timer.setBreachedAt(now);
        timer.setEscalationLevel((short) Math.max(timer.getEscalationLevel(), 1));
        Map<String, Object> payload = timerPayload(timer, stage);
        if (timer.getKind() == SlaTimerKind.STAGE_SLA && stage != null) {
            notificationService.notifyAssigneeOrQueueAndCoordinators(
                    stage, request, NotificationEvent.SLA_BREACH, payload);
            audit(timer, request, stage, AuditAction.SLA_BREACH_RECORDED, payload);
            return;
        }
        if (timer.getKind() == SlaTimerKind.ACCEPTANCE) {
            notificationService.notifyManager(request, NotificationEvent.CONTROL_DEADLINE_BREACH, payload);
        } else {
            notificationService.notifyCoordinators(request, NotificationEvent.CONTROL_DEADLINE_BREACH, payload);
        }
        audit(timer, request, null, AuditAction.CONTROL_DEADLINE_BREACH_RECORDED, payload);
    }

    private void escalate(SlaTimer timer, Request request, RequestStage stage) {
        timer.setEscalationLevel((short) 2);
        Map<String, Object> payload = timerPayload(timer, stage);
        notificationService.notifyRoles(Set.of(Role.B2B_HEAD, Role.COORDINATOR),
                request, NotificationEvent.SLA_ESCALATION, payload);
        audit(timer, request, stage, AuditAction.SLA_ESCALATED, payload);
    }

    private OffsetDateTime nextEvent(SlaTimer timer, boolean stageTimer, OffsetDateTime now) {
        List<OffsetDateTime> candidates = new ArrayList<>();
        if (timer.getWarnedAt() == null && future(timer.getWarnAt(), now)) {
            candidates.add(timer.getWarnAt());
        }
        if (timer.getBreachedAt() == null && future(timer.getDueAt(), now)) {
            candidates.add(timer.getDueAt());
        }
        if (stageTimer && timer.getEscalationLevel() < 2 && future(timer.getEscalateAt(), now)) {
            candidates.add(timer.getEscalateAt());
        }
        return candidates.stream().min(OffsetDateTime::compareTo).orElse(null);
    }

    private Map<String, Object> timerPayload(SlaTimer timer, RequestStage stage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", timer.getKind().name());
        if (stage != null) {
            payload.put("stageId", stage.getId().toString());
            payload.put("stageType", stage.getType().name());
        } else {
            payload.put("deadlineKind", timer.getKind().name());
        }
        return payload;
    }

    private Map<String, Object> pausePayload(SlaTimer timer, RequestStage stage, SlaPause pause) {
        Map<String, Object> payload = timerPayload(timer, stage);
        payload.put("reasonCode", pause.getReasonCode().name());
        return payload;
    }

    private void audit(SlaTimer timer, Request request, RequestStage stage,
                       AuditAction action, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>(details);
        payload.put("timerId", timer.getId().toString());
        payload.put("kind", timer.getKind().name());
        if (stage != null) {
            payload.put("stageId", stage.getId().toString());
            payload.put("stageType", stage.getType().name());
        }
        auditService.recordSystem(request.getCompanyId(), request.getId(), action,
                "SLA_TIMER", timer.getId(), null, timer.getStatus().name(), null, payload);
    }

    private static boolean due(OffsetDateTime moment, OffsetDateTime now) {
        return moment != null && !now.isBefore(moment);
    }

    private static boolean future(OffsetDateTime moment, OffsetDateTime now) {
        return moment != null && moment.isAfter(now);
    }

    private static long minutesLeft(OffsetDateTime dueAt, OffsetDateTime now) {
        if (dueAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, dueAt).toMinutes());
    }
}
