package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.CommercialTermsPayload;
import com.b2borderflow.core.api.model.ContractConcludedPayload;
import com.b2borderflow.core.api.model.RequestView;
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
import com.b2borderflow.core.sla.PauseReasonCode;
import com.b2borderflow.core.sla.SlaCalendarFactory;
import com.b2borderflow.core.sla.SlaTimer;
import com.b2borderflow.core.sla.SlaTimerService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переходы заявки недели 2: коммерческие условия (T04), договор (T13), акт и
 * закрытие (T18). Каждый переход — одна транзакция: статус, этапы, аудит,
 * уведомления и таймеры коммитятся вместе (status-model.md, принцип 3).
 */
@Service
public class RequestLifecycleService {

    private final RequestRepository requestRepository;
    private final ServiceOfferRepository serviceOfferRepository;
    private final RequestAccessPolicy accessPolicy;
    private final RequestViewFactory viewFactory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StageService stageService;
    private final SlaTimerService slaTimerService;
    private final SlaCalendarFactory calendarFactory;
    private final RoutingService routingService;
    private final RoutingParametersFactory routingParametersFactory;
    private final Clock clock;

    public RequestLifecycleService(RequestRepository requestRepository,
                                   ServiceOfferRepository serviceOfferRepository,
                                   RequestAccessPolicy accessPolicy,
                                   RequestViewFactory viewFactory,
                                   CurrentUserProvider currentUserProvider,
                                   AuditService auditService,
                                   NotificationService notificationService,
                                   StageService stageService,
                                   SlaTimerService slaTimerService,
                                   SlaCalendarFactory calendarFactory,
                                   RoutingService routingService,
                                   RoutingParametersFactory routingParametersFactory,
                                   Clock clock) {
        this.requestRepository = requestRepository;
        this.serviceOfferRepository = serviceOfferRepository;
        this.accessPolicy = accessPolicy;
        this.viewFactory = viewFactory;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.stageService = stageService;
        this.slaTimerService = slaTimerService;
        this.calendarFactory = calendarFactory;
        this.routingService = routingService;
        this.routingParametersFactory = routingParametersFactory;
        this.clock = clock;
    }

    /** T04: коммерческие условия, предварительный маршрут и очередь ТС. */
    @Transactional
    public RequestView setCommercialTerms(UUID requestId, CommercialTermsPayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, payload.getVersion());
        if (request.getStatus() != RequestStatus.WITH_MANAGER) {
            throw ApiException.illegalTransition("Коммерческие условия фиксируются из статуса WITH_MANAGER");
        }

        ServiceOffer offer = selectedOffer(request);
        if (payload.getTariffType() == null) {
            throw ApiException.validation("Укажите тип тарифа",
                    List.of(new Violation("tariffType", "REQUIRED", "Тип тарифа обязателен")));
        }
        if (payload.getContractType() == null) {
            throw ApiException.validation("Укажите тип договора",
                    List.of(new Violation("contractType", "REQUIRED", "Тип договора обязателен")));
        }
        TariffType tariffType = TariffType.valueOf(payload.getTariffType().name());
        ContractType contractType = ContractType.valueOf(payload.getContractType().name());
        BigDecimal discount = discount(payload, tariffType);
        Integer speed = payload.getSpeedMbps() == null
                ? request.getRequestedSpeedMbps()
                : payload.getSpeedMbps();
        if (speed == null || speed <= 0) {
            throw ApiException.validation("Скорость должна быть больше нуля",
                    List.of(new Violation("speedMbps", "INVALID", "Укажите скорость больше нуля")));
        }

        offer.setTariffType(tariffType);
        offer.setDiscountPercent(discount);
        offer.setContractType(contractType);
        offer.setSpeedMbps(speed);
        serviceOfferRepository.saveAndFlush(offer);

        RoutePlan route = routingService.preliminary(routingParametersFactory.from(request, offer));

        String previousStatus = request.getStatus().name();
        OffsetDateTime now = OffsetDateTime.now(clock);
        request.setStatus(RequestStatus.TECH_CHECK);
        request.setStatusChangedAt(now);
        request.setRouteVersion(request.getRouteVersion() + 1);
        request.setRequestClass(route.requestClass().name());
        requestRepository.saveAndFlush(request);

        stageService.createStage(request, RequestStageType.TECH_CHECK, Role.TECH_SPECIALIST,
                null, RequestStageStatus.QUEUED, route.requiredByRules());

        Map<String, Object> details = new HashMap<>();
        details.put("tariffType", tariffType.name());
        details.put("discountPercent", discount.toPlainString());
        details.put("contractType", contractType.name());
        details.put("speedMbps", speed);
        details.put("requiredByRules", route.requiredByRules());
        details.put("requestClass", route.requestClass().name());
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_COMMERCIAL_TERMS_SET, "REQUEST", request.getId(),
                previousStatus, RequestStatus.TECH_CHECK.name(), null, details);
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.TECH_CHECK.name(), null, Map.of("transition", "T04"));
        notificationService.notifyAuthorAndManager(request, NotificationEvent.STATUS_CHANGED);
        return viewFactory.toView(request, actor, List.of());
    }

    /** Отметка «Договор отправлен»: автопауза ожидания подписи, статус не меняется. */
    @Transactional
    public RequestView markContractSent(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.CONTRACT_SIGNING) {
            throw ApiException.illegalTransition("Договор отправляется из статуса CONTRACT_SIGNING");
        }
        if (request.getContractSentAt() != null) {
            throw ApiException.illegalTransition("Договор уже отмечен отправленным");
        }

        request.setContractSentAt(OffsetDateTime.now(clock));
        requestRepository.saveAndFlush(request);

        stageService.findActiveStage(requestId, RequestStageType.CONTRACT_SIGNING)
                .ifPresent(stage -> slaTimerService.pauseForClientSignature(stage, null));

        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_CONTRACT_SENT, "REQUEST", request.getId(),
                request.getStatus().name(), request.getStatus().name(), null,
                Map.of("contractSentAt", request.getContractSentAt().toString()));
        return viewFactory.toView(request, actor, List.of());
    }

    /** T13 по отметке менеджера: номер и дата договора, переход в исполнение. */
    @Transactional
    public RequestView concludeContract(UUID requestId, ContractConcludedPayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, payload.getVersion());
        if (request.getStatus() != RequestStatus.CONTRACT_SIGNING) {
            throw ApiException.illegalTransition("Договор заключается из статуса CONTRACT_SIGNING");
        }
        if (payload.getContractNumber() == null || payload.getContractNumber().isBlank()) {
            throw ApiException.validation("Укажите номер договора",
                    List.of(new Violation("contractNumber", "REQUIRED", "Номер договора обязателен")));
        }
        if (payload.getConcludedAt() == null) {
            throw ApiException.validation("Укажите дату договора",
                    List.of(new Violation("concludedAt", "REQUIRED", "Дата договора обязательна")));
        }
        LocalDate today = LocalDate.now(clock.withZone(calendarFactory.businessZone()));
        if (payload.getConcludedAt().isAfter(today)) {
            throw ApiException.validation("Дата договора не может быть в будущем",
                    List.of(new Violation("concludedAt", "INVALID", "Дата не позже сегодняшней")));
        }

        ZoneId zone = calendarFactory.businessZone();
        OffsetDateTime concludedAt = payload.getConcludedAt().atStartOfDay(zone).toOffsetDateTime();
        transitionToExecution(request, actor, payload.getContractNumber().trim(), concludedAt,
                AuditAction.REQUEST_CONTRACT_CONCLUDED, "T13");
        return viewFactory.toView(request, actor, List.of());
    }

    /** T13 по акцепту оферты клиентом. */
    @Transactional
    public RequestView acceptOffer(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.CLIENT_OWNER, Role.CLIENT_REQUESTER);
        Request request = loadRequest(requestId);
        accessPolicy.assertCanView(request, actor);
        RequestService.requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.CONTRACT_SIGNING) {
            throw ApiException.illegalTransition("Акцепт оферты возможен только при подписании договора");
        }
        ServiceOffer offer = selectedOffer(request);
        if (offer.getContractType() != ContractType.OFFER) {
            throw ApiException.illegalTransition(
                    "Акцепт доступен только для договора-оферты; для других типов договор отмечает менеджер");
        }

        transitionToExecution(request, actor, null, OffsetDateTime.now(clock),
                AuditAction.REQUEST_OFFER_ACCEPTED, "T13");
        return viewFactory.toView(request, actor, List.of());
    }

    /** Отметка «Акт отправлен»: пауза CLOSING, новый срок приёмки и напоминание клиенту. */
    @Transactional
    public RequestView markActSent(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.ACTIVATED) {
            throw ApiException.illegalTransition("Акт отправляется из статуса ACTIVATED");
        }

        // Повторная отправка после устранения замечаний запускает новый срок приёмки.
        slaTimerService.stopControlTimer(requestId, com.b2borderflow.core.sla.SlaTimerKind.ACCEPTANCE);
        SlaTimer acceptance = slaTimerService.startControlTimer(
                request, com.b2borderflow.core.sla.SlaTimerKind.ACCEPTANCE);

        request.setActSentAt(OffsetDateTime.now(clock));
        requestRepository.saveAndFlush(request);

        stageService.findActiveStage(requestId, RequestStageType.CLOSING)
                .ifPresent(stage -> slaTimerService.pause(stage,
                        PauseReasonCode.AWAIT_CLIENT_SIGNATURE,
                        "Ожидание подписи акта", null, acceptance.getDueAt()));

        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_ACT_SENT, "REQUEST", request.getId(),
                request.getStatus().name(), request.getStatus().name(), null,
                Map.of("acceptanceDueAt", acceptance.getDueAt().toString()));
        notificationService.notifyUsers(Set.of(request.getCreatedBy()), request,
                NotificationEvent.ACT_SENT, Map.of());
        return viewFactory.toView(request, actor, List.of());
    }

    /** T18: акт подписан менеджером. */
    @Transactional
    public RequestView signAct(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.ACTIVATED) {
            throw ApiException.illegalTransition("Закрыть заявку можно из статуса ACTIVATED");
        }
        if (request.getActSentAt() == null) {
            throw ApiException.illegalTransition("Сначала отметьте отправку акта");
        }
        if (stageService.findActiveStage(requestId, RequestStageType.REMEDIATION).isPresent()) {
            throw ApiException.illegalTransition("Нельзя закрыть заявку во время устранения замечаний");
        }

        closeRequest(request, actor, false, AuditAction.REQUEST_ACT_SIGNED, "T18");
        return viewFactory.toView(request, actor, List.of());
    }

    /** T18: закрытие «Принято по умолчанию» после истечения срока приёмки. */
    @Transactional
    public RequestView closeByDefault(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);
        Request request = loadRequest(requestId);
        requireOwnerManager(request, actor);
        RequestService.requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.ACTIVATED) {
            throw ApiException.illegalTransition("Закрыть заявку можно из статуса ACTIVATED");
        }
        if (request.getActSentAt() == null) {
            throw ApiException.illegalTransition("Сначала отметьте отправку акта");
        }
        if (stageService.findActiveStage(requestId, RequestStageType.REMEDIATION).isPresent()) {
            throw ApiException.illegalTransition("Нельзя закрыть заявку во время устранения замечаний");
        }

        SlaTimer acceptance = slaTimerService.findAcceptanceTimer(requestId)
                .orElseThrow(() -> ApiException.illegalTransition("Срок приёмки не запущен"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (acceptance.getDueAt() == null || now.isBefore(acceptance.getDueAt())) {
            throw ApiException.illegalTransition(
                    "Срок приёмки ещё не истёк: закрытие по умолчанию недоступно");
        }

        closeRequest(request, actor, true, AuditAction.REQUEST_CLOSED_BY_DEFAULT, "T18");
        return viewFactory.toView(request, actor, List.of());
    }

    // --- внутренняя логика ---

    private void transitionToExecution(Request request, CurrentUser actor, String contractNumber,
                                       OffsetDateTime concludedAt, AuditAction action, String transition) {
        String previousStatus = request.getStatus().name();
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (contractNumber != null) {
            request.setContractNumber(contractNumber);
        }
        request.setContractConcludedAt(concludedAt);
        request.setCommitmentLevel((short) Math.max(request.getCommitmentLevel(), 1));
        request.setStatus(RequestStatus.IN_EXECUTION);
        request.setStatusChangedAt(now);
        requestRepository.saveAndFlush(request);

        stageService.findActiveStage(request.getId(), RequestStageType.CONTRACT_SIGNING)
                .ifPresent(stage -> stageService.finishStage(stage, RequestStageStatus.DONE, now));

        RequestStageType firstExecution = request.getTechResult() != null
                && TechResult.WITH_CONSTRUCTION.name().equals(request.getTechResult())
                ? RequestStageType.CONSTRUCTION
                : RequestStageType.INSTALLATION;
        stageService.createStage(request, firstExecution, Role.ENGINEER, null,
                RequestStageStatus.QUEUED, List.of());

        Map<String, Object> details = new HashMap<>();
        details.put("transition", transition);
        if (contractNumber != null) {
            details.put("contractNumber", contractNumber);
        }
        details.put("concludedAt", concludedAt.toString());
        auditService.record(actor, request.getCompanyId(), request.getId(), action,
                "REQUEST", request.getId(), previousStatus, RequestStatus.IN_EXECUTION.name(), null, details);
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.IN_EXECUTION.name(), null, Map.of("transition", transition));
        notificationService.notifyAuthorAndManager(request, NotificationEvent.STATUS_CHANGED);
    }

    private void closeRequest(Request request, CurrentUser actor, boolean acceptedByDefault,
                              AuditAction action, String transition) {
        String previousStatus = request.getStatus().name();
        OffsetDateTime now = OffsetDateTime.now(clock);

        request.setAcceptedByDefault(acceptedByDefault);
        request.setClosedAt(now);
        request.setStatus(RequestStatus.CLOSED);
        request.setStatusChangedAt(now);
        requestRepository.saveAndFlush(request);

        stageService.findActiveStage(request.getId(), RequestStageType.CLOSING)
                .ifPresent(stage -> stageService.finishStage(stage, RequestStageStatus.DONE, now));
        slaTimerService.stopRequestTimers(request.getId());

        auditService.record(actor, request.getCompanyId(), request.getId(), action,
                "REQUEST", request.getId(), previousStatus, RequestStatus.CLOSED.name(), null,
                Map.of("acceptedByDefault", acceptedByDefault, "transition", transition));
        auditService.record(actor, request.getCompanyId(), request.getId(),
                AuditAction.REQUEST_STATUS_CHANGED, "REQUEST", request.getId(),
                previousStatus, RequestStatus.CLOSED.name(), null, Map.of("transition", transition));
        notificationService.notifyAuthorAndManager(request, NotificationEvent.REQUEST_CLOSED);
    }

    private Request loadRequest(UUID requestId) {
        return requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private ServiceOffer selectedOffer(Request request) {
        if (request.getSelectedOfferId() == null) {
            throw new ApiException(ErrorCode.ILLEGAL_TRANSITION, "У заявки нет выбранного предложения");
        }
        return serviceOfferRepository.findById(request.getSelectedOfferId())
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_TRANSITION,
                        "Выбранное предложение не найдено"));
    }

    private static void requireOwnerManager(Request request, CurrentUser actor) {
        if (request.getManagerId() == null || !request.getManagerId().equals(actor.id())) {
            throw ApiException.accessDenied("Действие выполняет менеджер заявки");
        }
    }

    private static BigDecimal discount(CommercialTermsPayload payload, TariffType tariffType) {
        String raw = payload.getDiscountPercent();
        if (raw == null || raw.isBlank()) {
            if (tariffType == TariffType.INDIVIDUAL) {
                throw ApiException.validation("Для индивидуального тарифа нужна скидка",
                        List.of(new Violation("discountPercent", "REQUIRED", "Скидка обязательна")));
            }
            return BigDecimal.ZERO;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw ApiException.validation("Скидка должна быть числом",
                    List.of(new Violation("discountPercent", "INVALID", "Неверный формат")));
        }
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.validation("Скидка должна быть от 0 до 100",
                    List.of(new Violation("discountPercent", "INVALID", "Диапазон 0.00–100.00")));
        }
        return value;
    }
}
