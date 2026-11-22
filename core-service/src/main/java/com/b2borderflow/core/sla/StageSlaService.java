package com.b2borderflow.core.sla;

import com.b2borderflow.core.api.model.SlaExtendPausePayload;
import com.b2borderflow.core.api.model.SlaPausePayload;
import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestRepository;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.request.RequestStageRepository;
import com.b2borderflow.core.request.RequestStageStatus;
import com.b2borderflow.core.request.StageViewMapper;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ручные паузы координатора (docs/system/sla.md, 6.2; plan-week2.md, п. 12).
 * Допустимы только {@code AWAIT_CLIENT_DATA}, {@code NO_ACCESS} и
 * {@code CLIENT_RESCHEDULED}; комментарий обязателен.
 */
@Service
public class StageSlaService {

    private static final Set<RequestStageStatus> PAUSABLE = EnumSet.of(
            RequestStageStatus.QUEUED, RequestStageStatus.IN_PROGRESS);

    private final RequestStageRepository stageRepository;
    private final RequestRepository requestRepository;
    private final SlaTimerRepository timerRepository;
    private final SlaTimerService timerService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final StageViewMapper stageViewMapper;

    public StageSlaService(RequestStageRepository stageRepository,
                           RequestRepository requestRepository,
                           SlaTimerRepository timerRepository,
                           SlaTimerService timerService,
                           CurrentUserProvider currentUserProvider,
                           AuditService auditService,
                           StageViewMapper stageViewMapper) {
        this.stageRepository = stageRepository;
        this.requestRepository = requestRepository;
        this.timerRepository = timerRepository;
        this.timerService = timerService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.stageViewMapper = stageViewMapper;
    }

    @Transactional
    public StageView pause(UUID stageId, SlaPausePayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);
        RequestStage stage = loadStage(stageId);
        requireVersion(stage, payload.getVersion());
        requireActive(stage);
        if (payload.getComment() == null || payload.getComment().isBlank()) {
            throw ApiException.validation("Комментарий к паузе обязателен",
                    java.util.List.of(new Violation("comment", "REQUIRED", "Комментарий обязателен")));
        }
        PauseReasonCode reason = manualReason(payload.getReasonCode());

        SlaPause pause = timerService.pause(stage, reason, payload.getComment(), actor.id(), null);
        if (pause == null) {
            throw ApiException.illegalTransition("У этапа нет активного таймера SLA");
        }
        Request request = loadRequest(stage);
        audit(actor, request, stage, AuditAction.SLA_PAUSE_SET,
                Map.of("reasonCode", reason.name(), "pauseId", pause.getId().toString()));
        return stageViewMapper.toView(stage, request.getNumber(), false);
    }

    @Transactional
    public StageView resume(UUID stageId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);
        RequestStage stage = loadStage(stageId);
        requireVersion(stage, expectedVersion);
        requireActive(stage);
        SlaTimer timer = activeTimer(stage);
        if (!timerService.resume(timer)) {
            throw ApiException.illegalTransition("На этапе нет активной паузы SLA");
        }
        Request request = loadRequest(stage);
        audit(actor, request, stage, AuditAction.SLA_PAUSE_RESUMED, Map.of());
        return stageViewMapper.toView(stage, request.getNumber(), false);
    }

    @Transactional
    public StageView extend(UUID stageId, SlaExtendPausePayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);
        RequestStage stage = loadStage(stageId);
        requireVersion(stage, payload.getVersion());
        requireActive(stage);
        if (payload.getComment() == null || payload.getComment().isBlank()) {
            throw ApiException.validation("Комментарий к продлению паузы обязателен",
                    java.util.List.of(new Violation("comment", "REQUIRED", "Комментарий обязателен")));
        }
        SlaTimer timer = activeTimer(stage);
        if (!timerService.extendPause(timer, actor.id())) {
            throw ApiException.illegalTransition("На этапе нет активной паузы SLA");
        }
        Request request = loadRequest(stage);
        audit(actor, request, stage, AuditAction.SLA_PAUSE_EXTENDED,
                Map.of("comment", payload.getComment()));
        return stageViewMapper.toView(stage, request.getNumber(), false);
    }

    private SlaTimer activeTimer(RequestStage stage) {
        return timerRepository.findFirstByStageIdAndStatusIn(
                        stage.getId(), EnumSet.of(SlaTimerStatus.RUNNING, SlaTimerStatus.PAUSED))
                .orElseThrow(() -> ApiException.illegalTransition("У этапа нет активного таймера SLA"));
    }

    private RequestStage loadStage(UUID stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> ApiException.notFound("Этап не найден"));
    }

    private Request loadRequest(RequestStage stage) {
        return requestRepository.findByIdAndDeletedAtIsNull(stage.getRequestId())
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private static void requireVersion(RequestStage stage, Long expectedVersion) {
        if (expectedVersion == null || stage.getVersion() != expectedVersion) {
            throw ApiException.versionConflict(
                    "Версия этапа устарела: актуальная версия " + stage.getVersion());
        }
    }

    private static void requireActive(RequestStage stage) {
        if (!PAUSABLE.contains(stage.getStatus())) {
            throw new ApiException(ErrorCode.STAGE_ALREADY_DECIDED,
                    "Пауза доступна только для этапа в очереди или работе");
        }
    }

    private static PauseReasonCode manualReason(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.PAUSE_REASON_INVALID, "Причина паузы обязательна");
        }
        PauseReasonCode reason;
        try {
            reason = PauseReasonCode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.PAUSE_REASON_INVALID,
                    "Неизвестная причина паузы: " + raw);
        }
        if (!reason.isManualByCoordinator()) {
            throw new ApiException(ErrorCode.PAUSE_REASON_INVALID,
                    "Причина " + reason + " недоступна для ручной паузы");
        }
        return reason;
    }

    private void audit(CurrentUser actor, Request request, RequestStage stage,
                       AuditAction action, Map<String, Object> details) {
        Map<String, Object> payload = new java.util.HashMap<>(details);
        payload.put("stageId", stage.getId().toString());
        payload.put("stageType", stage.getType().name());
        auditService.record(actor, request.getCompanyId(), request.getId(), action,
                "STAGE", stage.getId(), null, stage.getStatus().name(), null, payload);
    }
}
