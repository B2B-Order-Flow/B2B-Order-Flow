package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.StageAssignPayload;
import com.b2borderflow.core.api.model.StagePage;
import com.b2borderflow.core.api.model.StageStatus;
import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.api.model.TechResultPayload;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.notification.NotificationEvent;
import com.b2borderflow.core.notification.NotificationService;
import com.b2borderflow.core.routing.RoutePlan;
import com.b2borderflow.core.routing.RoutingParametersFactory;
import com.b2borderflow.core.routing.RoutingService;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import com.b2borderflow.core.sla.SlaTimer;
import com.b2borderflow.core.sla.SlaTimerService;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Этапы маршрута: очередь и «мои задачи», взятие и назначение, результат ТВ,
 * завершение монтажа и активация (docs/system/status-model.md, 6.1, 6.1.2).
 *
 * <p>Каждый метод — одна транзакция: статус этапа/заявки, аудит, уведомления и
 * таймер SLA коммитятся вместе. Этапы очереди создаются в {@code QUEUED} без
 * исполнителя; {@code CONTRACT_SIGNING} и {@code CLOSING} сразу назначены менеджеру
 * и идут в {@code IN_PROGRESS}.</p>
 */
@Service
public class StageService {

    /** Роли, которые берут этапы из очереди; координатор назначает, но не берёт. */
    static final Set<Role> EXECUTOR_ROLES = EnumSet.of(
            Role.TECH_SPECIALIST,
            Role.LAWYER,
            Role.FINANCE_CONTROLLER,
            Role.PROCUREMENT_SPECIALIST,
            Role.B2B_HEAD,
            Role.ENGINEER);

    private static final Set<RequestStageStatus> ACTIVE_STAGE_STATUSES =
            EnumSet.of(RequestStageStatus.QUEUED, RequestStageStatus.IN_PROGRESS);
    private static final Set<RequestStageStatus> OPEN_STAGE_STATUSES = EnumSet.of(
            RequestStageStatus.QUEUED,
            RequestStageStatus.IN_PROGRESS,
            RequestStageStatus.RETURNED);

    private final RequestStageRepository stageRepository;
    private final RequestRepository requestRepository;
    private final AppUserRepository userRepository;
    private final com.b2borderflow.core.company.OperatorRoleRepository operatorRoleRepository;
    private final ServiceOfferRepository serviceOfferRepository;
    private final RejectionReasonRepository rejectionReasonRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SlaTimerService slaTimerService;
    private final RoutingService routingService;
    private final RoutingParametersFactory routingParametersFactory;
    private final StageViewMapper stageViewMapper;
    private final Clock clock;

    public StageService(RequestStageRepository stageRepository,
                        RequestRepository requestRepository,
                        AppUserRepository userRepository,
                        com.b2borderflow.core.company.OperatorRoleRepository operatorRoleRepository,
                        ServiceOfferRepository serviceOfferRepository,
                        RejectionReasonRepository rejectionReasonRepository,
                        CurrentUserProvider currentUserProvider,
                        AuditService auditService,
                        NotificationService notificationService,
                        SlaTimerService slaTimerService,
                        RoutingService routingService,
                        RoutingParametersFactory routingParametersFactory,
                        StageViewMapper stageViewMapper,
                        Clock clock) {
        this.stageRepository = stageRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.serviceOfferRepository = serviceOfferRepository;
        this.rejectionReasonRepository = rejectionReasonRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.slaTimerService = slaTimerService;
        this.routingService = routingService;
        this.routingParametersFactory = routingParametersFactory;
        this.stageViewMapper = stageViewMapper;
        this.clock = clock;
    }

    // --- чтение ---

    @Transactional(readOnly = true)
    public StagePage list(com.b2borderflow.core.api.model.Role queueRole, StageStatus status,
                          int page, int size, String sort) {
        CurrentUser actor = currentUserProvider.current();
        Role role = actor.requireAnyRole(
                Role.TECH_SPECIALIST, Role.LAWYER, Role.FINANCE_CONTROLLER,
                Role.PROCUREMENT_SPECIALIST, Role.B2B_HEAD, Role.ENGINEER, Role.COORDINATOR);

        Role filterRole = queueRole == null ? null : Role.valueOf(queueRole.name());
        if (role == Role.COORDINATOR) {
            // Координатор видит очереди всех ролей, фильтр необязателен.
        } else if (filterRole == null) {
            filterRole = role;
        } else if (filterRole != role) {
            throw ApiException.accessDenied("Исполнитель видит только очередь своей роли");
        }

        RequestStageStatus statusFilter = status == null
                ? RequestStageStatus.QUEUED
                : RequestStageStatus.valueOf(status.name());

        List<RequestStage> stages = filterRole == null
                ? stageRepository.findByStatusOrderByCreatedAtAsc(statusFilter)
                : stageRepository.findByQueueRoleAndStatusOrderByCreatedAtAsc(filterRole, statusFilter);

        return toPage(stages, actor, page, size, sort);
    }

    @Transactional(readOnly = true)
    public StagePage my(int page, int size) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(
                Role.TECH_SPECIALIST, Role.LAWYER, Role.FINANCE_CONTROLLER,
                Role.PROCUREMENT_SPECIALIST, Role.B2B_HEAD, Role.ENGINEER);

        List<RequestStage> stages = stageRepository
                .findByAssigneeIdAndStatusInOrderByCreatedAtAsc(actor.id(), ACTIVE_STAGE_STATUSES);
        return toPage(stages, actor, page, size, null);
    }

    // --- команды ---

    @Transactional
    public StageView claim(UUID stageId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        Role role = actor.requireAnyRole(
                Role.TECH_SPECIALIST, Role.LAWYER, Role.FINANCE_CONTROLLER,
                Role.PROCUREMENT_SPECIALIST, Role.B2B_HEAD, Role.ENGINEER);

        RequestStage stage = loadStage(stageId);
        Request request = loadRequest(stage);
        requireStageVersion(stage, expectedVersion);
        if (stage.getStatus() != RequestStageStatus.QUEUED) {
            throw stageAlreadyDecided(stage);
        }
        if (stage.getQueueRole() != role) {
            throw notInQueue();
        }
        if (stage.getAssigneeId() != null) {
            throw stageAlreadyDecided(stage);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        stage.setAssigneeId(actor.id());
        stage.setStatus(RequestStageStatus.IN_PROGRESS);
        stage.setTakenAt(now);
        stage.setNeedsReassignment(false);
        RequestStage saved = stageRepository.saveAndFlush(stage);

        markWorkStartedIfNeeded(request, saved, actor, now);
        auditStage(actor, request, saved, AuditAction.STAGE_CLAIMED,
                "Этап взят из очереди", null);
        notificationService.notifyAssignee(saved, request);
        return stageViewMapper.toView(saved, request.getNumber(), false);
    }

    @Transactional
    public StageView assign(UUID stageId, StageAssignPayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);

        RequestStage stage = loadStage(stageId);
        Request request = loadRequest(stage);
        requireStageVersion(stage, payload.getVersion());
        if (!ACTIVE_STAGE_STATUSES.contains(stage.getStatus())) {
            throw stageAlreadyDecided(stage);
        }
        if (stage.getQueueRole() == null) {
            throw ApiException.illegalTransition(
                    "Этап " + stage.getType() + " назначается автоматически и не может быть переназначен");
        }

        AppUser assignee = userRepository.findById(payload.getAssigneeId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.validation("Исполнитель не найден или деактивирован",
                        List.of(new Violation("assigneeId", "INVALID", "Укажите активного пользователя"))));
        if (!operatorRoleRepository.existsByIdUserIdAndIdRole(assignee.getId(), stage.getQueueRole())) {
            throw ApiException.validation("У пользователя нет роли очереди этапа",
                    List.of(new Violation("assigneeId", "INVALID",
                            "Нужна роль " + stage.getQueueRole())));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        stage.setAssigneeId(assignee.getId());
        stage.setStatus(RequestStageStatus.IN_PROGRESS);
        stage.setNeedsReassignment(false);
        if (stage.getTakenAt() == null) {
            stage.setTakenAt(now);
        }
        RequestStage saved = stageRepository.saveAndFlush(stage);

        markWorkStartedIfNeeded(request, saved, actor, now);
        auditStage(actor, request, saved, AuditAction.STAGE_ASSIGNED,
                "Этап назначен координатором", null);
        notificationService.notifyAssignee(saved, request);
        return stageViewMapper.toView(saved, request.getNumber(), false);
    }

    @Transactional
    public StageView techResult(UUID stageId, TechResultPayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.TECH_SPECIALIST);

        RequestStage stage = loadStage(stageId);
        Request request = loadRequest(stage);
        requireStageVersion(stage, payload.getVersion());

        if (stage.getType() != RequestStageType.TECH_CHECK) {
            throw ApiException.illegalTransition(
                    "Результат ТВ вносится только на этапе TECH_CHECK; повторная проверка — неделя 3");
        }
        if (stage.getStatus() != RequestStageStatus.IN_PROGRESS
                || !actor.id().equals(stage.getAssigneeId())) {
            throw stageAlreadyDecided(stage);
        }
        if (request.getStatus() != RequestStatus.TECH_CHECK) {
            throw ApiException.illegalTransition("Заявка не находится на проверке ТВ");
        }

        com.b2borderflow.core.api.model.TechResult requested = payload.getResult();
        if (requested == null) {
            throw ApiException.validation("Укажите результат ТВ",
                    List.of(new Violation("result", "REQUIRED", "Результат обязателен")));
        }
        com.b2borderflow.core.request.TechResult result =
                com.b2borderflow.core.request.TechResult.valueOf(requested.name());
        if (result == com.b2borderflow.core.request.TechResult.WITH_CONSTRUCTION) {
            throw ApiException.illegalTransition(
                    "T06 и стройка появятся на неделе 3; на неделе 2 результат WITH_CONSTRUCTION недоступен");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (result == com.b2borderflow.core.request.TechResult.NONE) {
            rejectByTechResult(stage, request, actor, payload, now);
            return stageViewMapper.toView(stage, request.getNumber(), false);
        }
        return acceptByTechResult(stage, request, actor, now);
    }

    @Transactional
    public StageView complete(UUID stageId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.ENGINEER);

        RequestStage stage = loadStage(stageId);
        Request request = loadRequest(stage);
        requireStageVersion(stage, expectedVersion);

        if (stage.getType() != RequestStageType.INSTALLATION
                && stage.getType() != RequestStageType.CONSTRUCTION) {
            throw ApiException.illegalTransition(
                    "Завершить командой complete можно только этап монтажа или стройки");
        }
        if (stage.getStatus() != RequestStageStatus.IN_PROGRESS
                || !actor.id().equals(stage.getAssigneeId())) {
            throw stageAlreadyDecided(stage);
        }
        if (request.getStatus() != RequestStatus.IN_EXECUTION) {
            throw ApiException.illegalTransition("Заявка не находится в исполнении");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        RequestStage finished = finishStage(stage, RequestStageStatus.DONE, now);
        auditStage(actor, request, finished, AuditAction.STAGE_COMPLETED,
                "Этап исполнения завершён", null);

        RequestStageType next = stage.getType() == RequestStageType.CONSTRUCTION
                ? RequestStageType.INSTALLATION
                : RequestStageType.ACTIVATION;
        createStage(request, next, Role.ENGINEER, null, RequestStageStatus.QUEUED, List.of());
        return stageViewMapper.toView(finished, request.getNumber(), false);
    }

    @Transactional
    public StageView activate(UUID stageId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.ENGINEER);

        RequestStage stage = loadStage(stageId);
        Request request = loadRequest(stage);
        requireStageVersion(stage, expectedVersion);

        if (stage.getType() != RequestStageType.ACTIVATION) {
            throw ApiException.illegalTransition("Активация выполняется с этапа ACTIVATION");
        }
        if (stage.getStatus() != RequestStageStatus.IN_PROGRESS
                || !actor.id().equals(stage.getAssigneeId())) {
            throw stageAlreadyDecided(stage);
        }
        if (request.getStatus() != RequestStatus.IN_EXECUTION) {
            throw ApiException.illegalTransition("Заявка не находится в исполнении");
        }
        if (!stageRepository.existsByRequestIdAndTypeAndStatus(
                request.getId(), RequestStageType.INSTALLATION, RequestStageStatus.DONE)) {
            throw ApiException.illegalTransition("Сначала завершите монтаж и настройку");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        RequestStage finished = finishStage(stage, RequestStageStatus.DONE, now);

        String previousStatus = request.getStatus().name();
        request.setStatus(RequestStatus.ACTIVATED);
        request.setStatusChangedAt(now);
        request.setActivatedAt(now);
        request.setCommitmentLevel((short) Math.max(request.getCommitmentLevel(), 3));
        requestRepository.saveAndFlush(request);

        RequestStage closing = createStage(request, RequestStageType.CLOSING,
                Role.SALES_MANAGER, request.getManagerId(), RequestStageStatus.IN_PROGRESS, List.of());

        auditStage(actor, request, finished, AuditAction.STAGE_ACTIVATED,
                "Услуга активирована", Map.of("closingStageId", closing.getId().toString()));
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.ACTIVATED.name(), null,
                Map.of("transition", "T16"));
        notificationService.notifyAuthorAndManager(request, NotificationEvent.SERVICE_ACTIVATED);
        return stageViewMapper.toView(finished, request.getNumber(), false);
    }

    // --- создание и завершение этапов (используются переходами заявки) ---

    @Transactional
    public RequestStage createStage(Request request, RequestStageType type, Role queueRole,
                                    UUID assigneeId, RequestStageStatus status, List<String> requiredByRules) {
        RequestStage stage = new RequestStage();
        stage.setRequestId(request.getId());
        stage.setRouteVersion(request.getRouteVersion());
        stage.setType(type);
        stage.setStatus(status);
        stage.setQueueRole(queueRole);
        stage.setAssigneeId(assigneeId);
        stage.setRequiredByRules(new ArrayList<>(requiredByRules == null ? List.of() : requiredByRules));
        if (status == RequestStageStatus.IN_PROGRESS) {
            stage.setTakenAt(OffsetDateTime.now(clock));
        }
        RequestStage saved = stageRepository.saveAndFlush(stage);
        slaTimerService.startStageTimer(request, saved);

        if (status == RequestStageStatus.QUEUED) {
            notificationService.notifyQueue(saved, request);
        }
        return saved;
    }

    @Transactional
    public RequestStage finishStage(RequestStage stage, RequestStageStatus status, OffsetDateTime now) {
        stage.setStatus(status);
        stage.setFinishedAt(now);
        RequestStage saved = stageRepository.saveAndFlush(stage);
        slaTimerService.stopStageTimer(saved);
        return saved;
    }

    @Transactional
    public java.util.Optional<RequestStage> findActiveStage(UUID requestId, RequestStageType type) {
        return stageRepository.findFirstByRequestIdAndTypeAndStatusInOrderByCreatedAtDesc(
                requestId, type, OPEN_STAGE_STATUSES);
    }

    // --- внутренняя логика ---

    private void rejectByTechResult(RequestStage stage, Request request, CurrentUser actor,
                                    TechResultPayload payload, OffsetDateTime now) {
        String reasonCode = payload.getReasonCode();
        if (reasonCode == null || reasonCode.isBlank()) {
            throw ApiException.validation("Укажите причину отказа",
                    List.of(new Violation("reasonCode", "REQUIRED", "Причина обязательна")));
        }
        rejectionReasonRepository.findByCodeAndActiveTrue(reasonCode)
                .orElseThrow(() -> ApiException.validation("Неизвестная причина отказа",
                        List.of(new Violation("reasonCode", "INVALID", "Код отсутствует в справочнике"))));
        if ("OTHER".equals(reasonCode)
                && (payload.getComment() == null || payload.getComment().isBlank())) {
            throw ApiException.validation("Для причины OTHER нужен комментарий",
                    List.of(new Violation("comment", "REQUIRED", "Комментарий обязателен")));
        }

        String previousStatus = request.getStatus().name();
        request.setTechResult(com.b2borderflow.core.request.TechResult.NONE.name());
        request.setTechRejectReasonCode(reasonCode);
        request.setTechRejectReason(payload.getComment());
        request.setStatus(RequestStatus.REJECTED);
        request.setStatusChangedAt(now);
        requestRepository.saveAndFlush(request);

        finishStage(stage, RequestStageStatus.DONE, now);
        cancelOtherOpenStages(request, stage, actor, now);
        slaTimerService.stopRequestTimers(request.getId());

        Map<String, Object> details = new HashMap<>();
        details.put("result", "NONE");
        details.put("reasonCode", reasonCode);
        auditStage(actor, request, stage, AuditAction.REQUEST_TECH_RESULT_SET,
                payload.getComment(), details);
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.REJECTED.name(), null, Map.of("transition", "T05"));
        notificationService.notifyUsers(notificationService.authorAndManager(request), request,
                NotificationEvent.TECH_RESULT_NONE,
                Map.of("reasonCode", reasonCode));
    }

    private StageView acceptByTechResult(RequestStage stage, Request request, CurrentUser actor,
                                         OffsetDateTime now) {
        ServiceOffer offer = request.getSelectedOfferId() == null
                ? null
                : serviceOfferRepository.findById(request.getSelectedOfferId()).orElse(null);
        RoutePlan finalRoute = routingService.finalRoute(routingParametersFactory.from(request, offer));
        if (finalRoute.requiresApprovals()) {
            throw ApiException.illegalTransition(
                    "По окончательному маршруту нужны согласования (T06); они появятся на неделе 3");
        }
        if (request.getManagerId() == null) {
            throw ApiException.illegalTransition("У заявки не назначен менеджер");
        }

        String previousStatus = request.getStatus().name();
        request.setTechResult(com.b2borderflow.core.request.TechResult.AVAILABLE.name());
        request.setRouteVersion(request.getRouteVersion() + 1);
        request.setRequestClass(finalRoute.requestClass().name());
        request.setStatus(RequestStatus.CONTRACT_SIGNING);
        request.setStatusChangedAt(now);
        requestRepository.saveAndFlush(request);

        finishStage(stage, RequestStageStatus.DONE, now);
        createStage(request, RequestStageType.CONTRACT_SIGNING, null,
                request.getManagerId(), RequestStageStatus.IN_PROGRESS, finalRoute.requiredByRules());

        Map<String, Object> details = new HashMap<>();
        details.put("result", "AVAILABLE");
        details.put("requiredByRules", finalRoute.requiredByRules());
        details.put("requestClass", finalRoute.requestClass().name());
        auditStage(actor, request, stage, AuditAction.REQUEST_TECH_RESULT_SET,
                null, details);
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.CONTRACT_SIGNING.name(), null,
                Map.of("transition", "T07"));
        notificationService.notifyAuthorAndManager(request, NotificationEvent.CONTRACT_READY);
        return stageViewMapper.toView(stage, request.getNumber(), false);
    }

    private void cancelOtherOpenStages(Request request, RequestStage completed,
                                       CurrentUser actor, OffsetDateTime now) {
        for (RequestStage other : stageRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())) {
            if (!other.getId().equals(completed.getId())
                    && OPEN_STAGE_STATUSES.contains(other.getStatus())) {
                other.setStatus(RequestStageStatus.CANCELLED);
                other.setFinishedAt(now);
                stageRepository.save(other);
                slaTimerService.stopStageTimer(other);
            }
        }
    }

    private void markWorkStartedIfNeeded(Request request, RequestStage stage,
                                         CurrentUser actor, OffsetDateTime now) {
        if (!stage.getType().isFirstExecution() || request.getWorkStartedAt() != null) {
            return;
        }
        request.setWorkStartedAt(now);
        request.setCommitmentLevel((short) Math.max(request.getCommitmentLevel(), 2));
        requestRepository.saveAndFlush(request);
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_WORK_STARTED, "REQUEST", request.getId(),
                request.getStatus().name(), request.getStatus().name(), null,
                Map.of("transition", "T14", "stageId", stage.getId().toString()));
    }

    private StagePage toPage(List<RequestStage> stages, CurrentUser actor, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validation("Некорректная пагинация",
                    List.of(new Violation("page", "INVALID", "page >= 0, size от 1 до 100")));
        }

        Map<UUID, SlaTimer> timers = new HashMap<>();
        for (RequestStage stage : stages) {
            slaTimerService.findActiveStageTimer(stage.getId()).ifPresent(timer -> timers.put(stage.getId(), timer));
        }
        Comparator<RequestStage> bySla = Comparator
                .comparing((RequestStage stage) -> timers.get(stage.getId()),
                        Comparator.nullsLast(Comparator.comparing(SlaTimer::getDueAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))))
                .thenComparing(RequestStage::getCreatedAt);
        List<RequestStage> sorted = new ArrayList<>(stages);
        sorted.sort(bySla);

        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        List<RequestStage> pageContent = sorted.subList(from, to);

        List<UUID> requestIds = pageContent.stream().map(RequestStage::getRequestId).distinct().toList();
        Map<UUID, String> numbers = new LinkedHashMap<>();
        requestRepository.findAllById(requestIds).forEach(request -> numbers.put(request.getId(), request.getNumber()));

        boolean clientView = actor.effectiveActiveRole().isClientRole();
        return new StagePage()
                .page(new com.b2borderflow.core.api.model.PageInfo()
                        .number(page)
                        .size(size)
                        .totalElements((long) sorted.size())
                        .totalPages((int) Math.ceil((double) sorted.size() / size)))
                .content(pageContent.stream()
                        .map(stage -> stageViewMapper.toView(
                                stage, numbers.get(stage.getRequestId()), clientView))
                        .toList());
    }

    private RequestStage loadStage(UUID stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> ApiException.notFound("Этап не найден"));
    }

    private Request loadRequest(RequestStage stage) {
        return requestRepository.findByIdAndDeletedAtIsNull(stage.getRequestId())
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private static void requireStageVersion(RequestStage stage, Long expectedVersion) {
        if (expectedVersion == null || stage.getVersion() != expectedVersion) {
            throw ApiException.versionConflict(
                    "Версия этапа устарела: актуальная версия " + stage.getVersion());
        }
    }

    private ApiException notInQueue() {
        return new ApiException(ErrorCode.STAGE_NOT_IN_YOUR_QUEUE,
                "Этап не в очереди активной роли");
    }

    private static ApiException stageAlreadyDecided(RequestStage stage) {
        return new ApiException(ErrorCode.STAGE_ALREADY_DECIDED,
                "Этап " + stage.getType() + " уже закрыт или решение уже принято");
    }

    private void auditStage(CurrentUser actor, Request request, RequestStage stage,
                            AuditAction action, String comment, Map<String, Object> details) {
        Map<String, Object> payload = details == null ? new HashMap<>() : new HashMap<>(details);
        payload.put("stageId", stage.getId().toString());
        payload.put("stageType", stage.getType().name());
        auditService.record(actor, request.getCompanyId(), request.getId(), action,
                "STAGE", stage.getId(), null, stage.getStatus().name(), comment, payload);
    }
}
