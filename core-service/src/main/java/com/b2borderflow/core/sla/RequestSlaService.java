package com.b2borderflow.core.sla;

import com.b2borderflow.core.api.model.SlaPauseView;
import com.b2borderflow.core.api.model.SlaTimerView;
import com.b2borderflow.core.api.model.SlaView;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestAccessPolicy;
import com.b2borderflow.core.request.RequestRepository;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.request.RequestStageRepository;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Представление SLA заявки: норматив, остаток, время по SLA и календарное,
 * журнал пауз (docs/system/sla.md, 8 и 10; api-contract.md, 6.8).
 * Клиенту SLA не показывается — сначала проверяется роль, поэтому ответ 403.
 */
@Service
public class RequestSlaService {

    private final RequestRepository requestRepository;
    private final RequestStageRepository stageRepository;
    private final SlaTimerRepository timerRepository;
    private final SlaPauseRepository pauseRepository;
    private final SlaTimerService timerService;
    private final RequestAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public RequestSlaService(RequestRepository requestRepository,
                             RequestStageRepository stageRepository,
                             SlaTimerRepository timerRepository,
                             SlaPauseRepository pauseRepository,
                             SlaTimerService timerService,
                             RequestAccessPolicy accessPolicy,
                             CurrentUserProvider currentUserProvider,
                             Clock clock) {
        this.requestRepository = requestRepository;
        this.stageRepository = stageRepository;
        this.timerRepository = timerRepository;
        this.pauseRepository = pauseRepository;
        this.timerService = timerService;
        this.accessPolicy = accessPolicy;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SlaView get(UUID requestId) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(
                Role.SALES_MANAGER,
                Role.TECH_SPECIALIST,
                Role.LAWYER,
                Role.FINANCE_CONTROLLER,
                Role.PROCUREMENT_SPECIALIST,
                Role.B2B_HEAD,
                Role.ENGINEER,
                Role.COORDINATOR);

        Request request = requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
        accessPolicy.assertCanView(request, actor);

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SlaTimer> timers = timerRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
        List<UUID> timerIds = timers.stream().map(SlaTimer::getId).toList();
        List<SlaPause> pauses = timerIds.isEmpty()
                ? List.of()
                : pauseRepository.findByTimerIdInOrderByStartedAtDesc(timerIds);

        Map<UUID, RequestStage> stages = new HashMap<>();
        List<UUID> stageIds = timers.stream()
                .map(SlaTimer::getStageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        stageRepository.findAllById(stageIds).forEach(stage -> stages.put(stage.getId(), stage));

        Map<UUID, SlaPause> activePauses = new HashMap<>();
        for (SlaPause pause : pauses) {
            if (pause.getEndedAt() == null) {
                activePauses.putIfAbsent(pause.getTimerId(), pause);
            }
        }

        List<SlaTimerView> timerViews = timers.stream()
                .map(timer -> toTimerView(timer, stages.get(timer.getStageId()),
                        activePauses.get(timer.getId()), now))
                .toList();
        List<SlaPauseView> pauseViews = pauses.stream().map(this::toPauseView).toList();

        return new SlaView()
                .requestId(requestId)
                .timers(timerViews)
                .pauses(pauseViews);
    }

    private SlaTimerView toTimerView(SlaTimer timer, RequestStage stage, SlaPause activePause,
                                     OffsetDateTime now) {
        long elapsed = timerService.elapsedSlaSeconds(timer, now);
        long normSeconds = timer.getNormMinutes() * 60L;
        long calendarElapsed = timer.getCreatedAt() == null
                ? 0
                : Math.max(0, Duration.between(timer.getCreatedAt(), now).toSeconds());
        boolean overdue = timer.getBreachedAt() != null
                || (timer.getStatus() != SlaTimerStatus.STOPPED
                        && timer.getDueAt() != null
                        && now.isAfter(timer.getDueAt()));

        return new SlaTimerView()
                .id(timer.getId())
                .kind(ApiEnums.slaTimerKind(timer.getKind()))
                .stageId(timer.getStageId())
                .stageType(stage == null ? null : ApiEnums.stageType(stage.getType()))
                .status(ApiEnums.slaTimerStatus(timer.getStatus()))
                .normMinutes(timer.getNormMinutes())
                .startedAt(timer.getStartedAt())
                .warnAt(timer.getWarnAt())
                .dueAt(timer.getDueAt())
                .escalateAt(timer.getEscalateAt())
                .elapsedSlaSeconds(elapsed)
                .remainingSlaSeconds(Math.max(0, normSeconds - elapsed))
                .calendarElapsedSeconds(calendarElapsed)
                .warnedAt(timer.getWarnedAt())
                .breachedAt(timer.getBreachedAt())
                .escalationLevel((int) timer.getEscalationLevel())
                .overdue(overdue)
                .pause(activePause == null ? null : toPauseView(activePause));
    }

    private SlaPauseView toPauseView(SlaPause pause) {
        return new SlaPauseView()
                .id(pause.getId())
                .reasonCode(ApiEnums.pauseReason(pause.getReasonCode()))
                .comment(pause.getComment())
                .startedAt(pause.getStartedAt())
                .endedAt(pause.getEndedAt())
                .limitAt(pause.getLimitAt())
                .extendedCount(pause.getExtendedCount())
                .setBy(parseSetBy(pause.getSetBy()));
    }

    private static UUID parseSetBy(String setBy) {
        if (setBy == null || SlaPause.SET_BY_SYSTEM.equals(setBy)) {
            return null;
        }
        try {
            return UUID.fromString(setBy);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
