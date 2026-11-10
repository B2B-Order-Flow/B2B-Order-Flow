package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.api.model.Warning;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.integration.ExternalCallResult;
import com.b2borderflow.core.integration.ExternalPayload;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переходы T01–T03b. Каждый переход — отдельная транзакция: статус, аудит,
 * ревизия и результаты внешних проверок коммитятся согласованно.
 *
 * <p>Внешние вызовы сюда не входят: оркестратор {@link RequestSubmissionService}
 * выполняет их между {@link #startSubmission} и {@link #completeSubmission}.
 */
@Service
public class RequestWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RequestWorkflowService.class);
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9()\\-\\s]{10,20}$");
    private static final int EXTERNAL_SYSTEMS = ExternalSystem.values().length;

    private final RequestRepository requestRepository;
    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;
    private final OperatorRoleRepository operatorRoleRepository;
    private final RequestRevisionRepository revisionRepository;
    private final ExternalCheckRepository externalCheckRepository;
    private final ServiceOfferRepository serviceOfferRepository;
    private final RequestAccessPolicy accessPolicy;
    private final RequestViewFactory viewFactory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    public RequestWorkflowService(RequestRepository requestRepository,
                                  CompanyRepository companyRepository,
                                  AppUserRepository userRepository,
                                  OperatorRoleRepository operatorRoleRepository,
                                  RequestRevisionRepository revisionRepository,
                                  ExternalCheckRepository externalCheckRepository,
                                  ServiceOfferRepository serviceOfferRepository,
                                  RequestAccessPolicy accessPolicy,
                                  RequestViewFactory viewFactory,
                                  CurrentUserProvider currentUserProvider,
                                  AuditService auditService,
                                  Clock clock) {
        this.requestRepository = requestRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.revisionRepository = revisionRepository;
        this.externalCheckRepository = externalCheckRepository;
        this.serviceOfferRepository = serviceOfferRepository;
        this.accessPolicy = accessPolicy;
        this.viewFactory = viewFactory;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Транзакция 1 отправки: проверки T01, ревизия №1, DATA_CHECK и аудит. */
    @Transactional
    public SubmissionStart startSubmission(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.CLIENT_OWNER, Role.CLIENT_REQUESTER);

        Request request = loadExisting(requestId);
        accessPolicy.assertCanView(request, actor);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.illegalTransition("Отправить можно только черновик");
        }
        RequestService.requireVersion(request, expectedVersion);

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_CONFIRMED, "Компания не найдена"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.COMPANY_NOT_CONFIRMED,
                    "Отправлять заявки может только подтверждённая компания");
        }
        validateForSubmission(request);

        int revisionNo = revisionRepository.findTopByRequestIdOrderByRevisionNoDesc(requestId)
                .map(revision -> revision.getRevisionNo() + 1)
                .orElse(1);

        RequestRevision revision = new RequestRevision();
        revision.setRequestId(requestId);
        revision.setRevisionNo(revisionNo);
        revision.setReason(RevisionReason.SUBMIT);
        revision.setCreatedBy(actor.id());
        revision.setParamsSnapshot(RequestViewFactory.snapshot(request));
        revisionRepository.save(revision);

        OffsetDateTime now = OffsetDateTime.now(clock);
        request.setStatus(RequestStatus.DATA_CHECK);
        request.setStatusChangedAt(now);
        request.setSubmittedAt(now);
        request.setIntegrationError(false);
        requestRepository.saveAndFlush(request);

        Map<String, Object> details = new HashMap<>();
        details.put("revisionNo", revisionNo);
        details.put("companyInn", company.getInn());
        auditService.record(actor, request.getCompanyId(), request.getId(), AuditAction.REQUEST_SUBMITTED,
                "REQUEST", request.getId(), RequestStatus.DRAFT.name(), RequestStatus.DATA_CHECK.name(),
                null, details);

        return new SubmissionStart(request.getId(), request.getCompanyId(), company.getInn());
    }

    /**
     * Транзакция 2: сохраняет результаты проверок, выполняет T02 или T03/T03b.
     * Переходы T02/T03/T03b выполняет системный актор, поэтому метод не требует
     * HTTP-контекста и пригоден для фонового повтора EC-10. {@code viewer} нужен
     * только для представления ответа; в фоне передаётся {@code null}.
     */
    @Transactional
    public RequestView completeSubmission(UUID requestId,
                                          Map<ExternalSystem, ExternalCallResult<?>> results,
                                          boolean recheck,
                                          CurrentUser viewer) {
        Request request = loadExisting(requestId);
        if (request.getStatus() != RequestStatus.DATA_CHECK) {
            throw ApiException.illegalTransition(
                    "Заявка уже вышла из статуса проверки данных: " + request.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ExternalCheck> checks = new ArrayList<>();
        for (Map.Entry<ExternalSystem, ExternalCallResult<?>> entry : results.entrySet()) {
            ExternalCallResult<?> result = entry.getValue();
            ExternalCheck check = new ExternalCheck();
            check.setRequestId(requestId);
            check.setSystem(entry.getKey());
            check.setStatus(result.ok() ? ExternalCheckStatus.OK : ExternalCheckStatus.ERROR);
            check.setAttempts(Math.max(result.attempts(), 1));
            check.setCheckedAt(now);
            check.setRawPayload(result.rawPayload());
            checks.add(check);
        }
        if (!checks.isEmpty()) {
            externalCheckRepository.saveAll(checks);
            externalCheckRepository.flush();
        }

        int successfulSystems = (int) externalCheckRepository.countSuccessfulSystems(requestId);
        boolean allChecked = successfulSystems >= EXTERNAL_SYSTEMS;

        Map<ExternalSystem, Map<String, Object>> successfulPayloads =
                latestSuccessfulPayloads(requestId);
        ExternalData data = externalData(successfulPayloads);

        List<Warning> warnings = List.of();

        if (allChecked) {
            applyExternalData(request, data);
            request.setIntegrationError(false);
            requestRepository.saveAndFlush(request);

            Map<String, Object> checkDetails = new HashMap<>();
            checkDetails.put("systems", ExternalSystem.values().length);
            checkDetails.put("actor", "SYSTEM");
            auditService.recordSystem(request.getCompanyId(), requestId,
                    AuditAction.REQUEST_DATA_CHECK_COMPLETED,
                    "REQUEST", requestId, RequestStatus.DATA_CHECK.name(), RequestStatus.DATA_CHECK.name(),
                    null, checkDetails);

            if (request.getManagerId() == null && data.managerId() != null) {
                request.setManagerId(data.managerId());
                request.setStatus(RequestStatus.WITH_MANAGER);
                request.setStatusChangedAt(now);
                requestRepository.saveAndFlush(request);

                Map<String, Object> details = new HashMap<>();
                details.put("managerId", data.managerId());
                details.put("source", "CRM");
                details.put("actor", "SYSTEM");
                auditService.recordSystem(request.getCompanyId(), requestId,
                        AuditAction.REQUEST_MANAGER_ASSIGNED,
                        "REQUEST", requestId, RequestStatus.DATA_CHECK.name(), RequestStatus.WITH_MANAGER.name(),
                        null, details);
            }
        } else {
            applyExternalData(request, data);
            request.setIntegrationError(true);
            requestRepository.saveAndFlush(request);

            Map<String, Object> details = new HashMap<>();
            details.put("successfulSystems", successfulSystems);
            details.put("failedSystems", failedSystems(results));
            details.put("actor", "SYSTEM");
            auditService.recordSystem(request.getCompanyId(), requestId,
                    AuditAction.REQUEST_DATA_CHECK_FAILED,
                    "REQUEST", requestId, RequestStatus.DATA_CHECK.name(), RequestStatus.DATA_CHECK.name(),
                    null, details);
            warnings = List.of(RequestViewFactory.integrationWarning());
        }

        if (recheck) {
            Map<String, Object> details = new HashMap<>();
            details.put("successfulSystems", successfulSystems);
            details.put("recheckedSystems", results.keySet().stream().map(Enum::name).toList());
            if (viewer == null) {
                auditService.recordSystem(request.getCompanyId(), requestId,
                        AuditAction.REQUEST_DATA_RECHECKED,
                        "REQUEST", requestId, request.getStatus().name(), request.getStatus().name(),
                        null, details);
            } else {
                auditService.record(viewer, request.getCompanyId(), requestId,
                        AuditAction.REQUEST_DATA_RECHECKED,
                        "REQUEST", requestId, request.getStatus().name(), request.getStatus().name(),
                        null, details);
            }
        }

        log.info("Проверка данных завершена: requestId={}, status={}, integrationError={}, successfulSystems={}",
                requestId, request.getStatus(), request.isIntegrationError(), successfulSystems);

        return viewFactory.toView(request, viewer, warnings);
    }

    /** T03b: менеджер берёт заявку из очереди. */
    @Transactional
    public RequestView claim(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.SALES_MANAGER);

        Request request = loadExisting(requestId);
        requireVersion(request, expectedVersion);
        if (request.getStatus() != RequestStatus.DATA_CHECK || request.getManagerId() != null) {
            throw ApiException.illegalTransition("Заявка уже не находится в очереди менеджеров");
        }
        requireDataChecked(request);

        request.setManagerId(actor.id());
        request.setStatus(RequestStatus.WITH_MANAGER);
        request.setStatusChangedAt(OffsetDateTime.now(clock));
        Request saved = requestRepository.saveAndFlush(request);

        Map<String, Object> details = new HashMap<>();
        details.put("managerId", actor.id());
        auditService.record(actor, saved.getCompanyId(), saved.getId(), AuditAction.REQUEST_MANAGER_CLAIMED,
                "REQUEST", saved.getId(), RequestStatus.DATA_CHECK.name(), RequestStatus.WITH_MANAGER.name(),
                null, details);

        return viewFactory.toView(saved, actor, List.of());
    }

    /** T03b или переназначение менеджера координатором (EC-19, 6.5). */
    @Transactional
    public RequestView assignManager(UUID requestId, UUID managerId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);

        AppUser manager = userRepository.findById(managerId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.validation("Менеджер не найден или деактивирован",
                        List.of(new Violation("managerId", "INVALID", "Укажите активного пользователя"))));
        if (!operatorRoleRepository.existsByIdUserIdAndIdRole(managerId, Role.SALES_MANAGER)) {
            throw ApiException.validation("Пользователь не является менеджером по продажам",
                    List.of(new Violation("managerId", "INVALID", "Нужна роль SALES_MANAGER")));
        }

        Request request = loadExisting(requestId);
        requireVersion(request, expectedVersion);

        if (request.getStatus().isTerminal()) {
            throw ApiException.illegalTransition("Нельзя назначить менеджера в терминальном статусе");
        }

        String previousStatus = request.getStatus().name();
        AuditAction action;
        String newStatus = previousStatus;
        UUID previousManagerId = request.getManagerId();

        if (request.getStatus() == RequestStatus.DATA_CHECK) {
            if (request.getManagerId() != null) {
                throw ApiException.illegalTransition("У заявки уже есть менеджер");
            }
            requireDataChecked(request);
            request.setManagerId(managerId);
            request.setStatus(RequestStatus.WITH_MANAGER);
            request.setStatusChangedAt(OffsetDateTime.now(clock));
            action = AuditAction.REQUEST_MANAGER_ASSIGNED;
            newStatus = RequestStatus.WITH_MANAGER.name();
        } else {
            if (managerId.equals(request.getManagerId())) {
                return viewFactory.toView(request, actor, List.of());
            }
            request.setManagerId(managerId);
            action = AuditAction.REQUEST_MANAGER_REASSIGNED;
        }

        Request saved = requestRepository.saveAndFlush(request);

        Map<String, Object> details = new HashMap<>();
        details.put("managerId", managerId);
        details.put("previousManagerId", previousManagerId);
        auditService.record(actor, saved.getCompanyId(), saved.getId(), action,
                "REQUEST", saved.getId(), previousStatus, newStatus, null, details);

        return viewFactory.toView(saved, actor, List.of());
    }

    /** Начало повторной проверки координатором (EC-10). */
    @Transactional
    public RecheckStart startRecheck(UUID requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.COORDINATOR);

        Request request = loadExisting(requestId);
        if (request.getStatus() != RequestStatus.DATA_CHECK) {
            throw ApiException.illegalTransition("Повторная проверка доступна только в DATA_CHECK");
        }
        requireVersion(request, expectedVersion);

        Set<ExternalSystem> successful = EnumSet.noneOf(ExternalSystem.class);
        for (ExternalCheck check : externalCheckRepository.findSuccessful(requestId)) {
            successful.add(check.getSystem());
        }

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> ApiException.notFound("Компания не найдена"));

        return new RecheckStart(requestId, company.getInn(), successful);
    }

    private Request loadExisting(UUID requestId) {
        return requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private void requireDataChecked(Request request) {
        if (request.isIntegrationError()
                || externalCheckRepository.countSuccessfulSystems(request.getId()) < EXTERNAL_SYSTEMS) {
            throw ApiException.illegalTransition("Данные заявки ещё не проверены");
        }
    }

    private static void requireVersion(Request request, long expectedVersion) {
        RequestService.requireVersion(request, expectedVersion);
    }

    private void validateForSubmission(Request request) {
        List<Violation> violations = new ArrayList<>();
        if (isBlank(request.getAddress())) {
            violations.add(new Violation("address", "REQUIRED", "Укажите адрес подключения"));
        }
        if (request.getRequestedSpeedMbps() == null || request.getRequestedSpeedMbps() <= 0) {
            violations.add(new Violation("requestedSpeedMbps", "REQUIRED", "Укажите скорость больше нуля"));
        }
        if (request.getDesiredDate() == null) {
            violations.add(new Violation("desiredDate", "REQUIRED", "Укажите желаемую дату подключения"));
        }
        if (isBlank(request.getContactName())) {
            violations.add(new Violation("contactName", "REQUIRED", "Укажите контактное лицо"));
        }
        if (isBlank(request.getContactPhone()) || !PHONE.matcher(request.getContactPhone()).matches()) {
            violations.add(new Violation("contactPhone", "INVALID", "Укажите корректный телефон"));
        }
        if (request.getSelectedOfferId() == null
                || serviceOfferRepository.findById(request.getSelectedOfferId()).isEmpty()) {
            violations.add(new Violation("offers", "REQUIRED", "В заявке должно быть предложение услуги"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation("Заявка заполнена не полностью", violations);
        }
    }

    private Map<ExternalSystem, Map<String, Object>> latestSuccessfulPayloads(UUID requestId) {
        Map<ExternalSystem, Map<String, Object>> result = new EnumMap<>(ExternalSystem.class);
        for (ExternalCheck check : externalCheckRepository.findSuccessful(requestId)) {
            result.putIfAbsent(check.getSystem(), check.getRawPayload());
        }
        return result;
    }

    private ExternalData externalData(Map<ExternalSystem, Map<String, Object>> payloads) {
        Map<String, Object> crm = payloads.getOrDefault(ExternalSystem.CRM, Map.of());
        Map<String, Object> billing = payloads.getOrDefault(ExternalSystem.BILLING, Map.of());
        Map<String, Object> registry = payloads.getOrDefault(ExternalSystem.REGISTRY, Map.of());

        boolean crmNotFound = ExternalPayload.intValue(crm, "httpStatus", 200) == 404;
        boolean billingNotFound = ExternalPayload.intValue(billing, "httpStatus", 200) == 404;
        boolean registryNotFound = ExternalPayload.intValue(registry, "httpStatus", 200) == 404;

        Segment segment = null;
        if (!crmNotFound) {
            String segmentValue = ExternalPayload.string(crm, "segment");
            if (segmentValue != null && !segmentValue.isBlank()) {
                try {
                    segment = Segment.valueOf(segmentValue);
                } catch (IllegalArgumentException ex) {
                    log.debug("CRM вернула неизвестный сегмент {}", segmentValue);
                }
            }
        }

        BigDecimal debt = billingNotFound
                ? BigDecimal.ZERO
                : ExternalPayload.decimal(billing, "debtAmount", BigDecimal.ZERO);
        boolean govCustomer = !registryNotFound && ExternalPayload.bool(registry, "isGovCustomer");

        UUID managerId = null;
        if (!crmNotFound) {
            String managerLogin = ExternalPayload.string(crm, "assignedManagerLogin");
            managerId = resolveManager(managerLogin);
        }

        return new ExternalData(segment, debt, govCustomer, managerId);
    }

    private UUID resolveManager(String login) {
        if (login == null || login.isBlank()) {
            return null;
        }
        AppUser user = userRepository.findByLoginIgnoreCaseAndActiveTrue(login).orElse(null);
        if (user == null || !operatorRoleRepository.existsByIdUserIdAndIdRole(user.getId(), Role.SALES_MANAGER)) {
            return null;
        }
        return user.getId();
    }

    private static void applyExternalData(Request request, ExternalData data) {
        request.setSegment(data.segment());
        request.setDebtAmount(data.debt());
        request.setGovCustomer(data.govCustomer());
        request.setCrmManagerId(data.managerId());
    }

    private static List<String> failedSystems(Map<ExternalSystem, ExternalCallResult<?>> results) {
        List<String> failed = new ArrayList<>();
        for (Map.Entry<ExternalSystem, ExternalCallResult<?>> entry : results.entrySet()) {
            if (!entry.getValue().ok()) {
                failed.add(entry.getKey().name());
            }
        }
        return failed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Данные, полученные из внешних систем и записываемые в заявку. */
    private record ExternalData(Segment segment, BigDecimal debt, boolean govCustomer, UUID managerId) {
    }

    /** Контекст первой транзакции отправки. */
    public record SubmissionStart(UUID requestId, UUID companyId, String companyInn) {
    }

    /** Контекст повторной проверки: известные успешные системы и ИНН компании. */
    public record RecheckStart(UUID requestId, String companyInn, Set<ExternalSystem> successfulSystems) {
    }
}
