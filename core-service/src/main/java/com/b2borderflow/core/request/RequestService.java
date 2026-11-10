package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.AuditEntry;
import com.b2borderflow.core.api.model.AuditEntryPage;
import com.b2borderflow.core.api.model.CreateRequestPayload;
import com.b2borderflow.core.api.model.PageInfo;
import com.b2borderflow.core.api.model.RequestPage;
import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.api.model.UpdateRequestPayload;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditLog;
import com.b2borderflow.core.audit.AuditLogRepository;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.idempotency.IdempotencyService;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Создание, правка, удаление и чтение заявок. Переходы статусов — в workflow-сервисе. */
@Service
public class RequestService {

    private static final String CREATE_ENDPOINT = "/internal/v1/requests";
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "statusChangedAt", "submittedAt", "desiredDate", "number", "status");

    private final RequestRepository requestRepository;
    private final ServiceOfferRepository serviceOfferRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogRepository auditLogRepository;
    private final RequestAccessPolicy accessPolicy;
    private final RequestViewFactory viewFactory;
    private final RequestNumberGenerator numberGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public RequestService(RequestRepository requestRepository,
                          ServiceOfferRepository serviceOfferRepository,
                          CompanyRepository companyRepository,
                          AuditLogRepository auditLogRepository,
                          RequestAccessPolicy accessPolicy,
                          RequestViewFactory viewFactory,
                          RequestNumberGenerator numberGenerator,
                          CurrentUserProvider currentUserProvider,
                          AuditService auditService,
                          IdempotencyService idempotencyService,
                          Clock clock) {
        this.requestRepository = requestRepository;
        this.serviceOfferRepository = serviceOfferRepository;
        this.companyRepository = companyRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessPolicy = accessPolicy;
        this.viewFactory = viewFactory;
        this.numberGenerator = numberGenerator;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public RequestView create(CreateRequestPayload payload, UUID idempotencyKey) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.CLIENT_OWNER, Role.CLIENT_REQUESTER);

        UUID companyId = actor.requireActiveCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_CONFIRMED, "Компания не найдена"));
        if (company.getStatus() != com.b2borderflow.core.company.CompanyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.COMPANY_NOT_CONFIRMED,
                    "Создавать заявки может только подтверждённая компания");
        }

        Optional<IdempotencyService.StoredResponse> stored =
                idempotencyService.find(actor.id(), idempotencyKey, CREATE_ENDPOINT, payload);
        if (stored.isPresent()) {
            return idempotencyService.convert(stored.get(), RequestView.class);
        }

        Request request = new Request();
        request.setNumber(numberGenerator.next());
        request.setCompanyId(companyId);
        request.setCreatedBy(actor.id());
        request.setStatus(RequestStatus.DRAFT);
        request.setStatusChangedAt(OffsetDateTime.now(clock));
        request.setCommitmentLevel((short) 0);
        request.setIntegrationError(false);
        applyDraftFields(request, payload);
        Request saved = requestRepository.save(request);

        ServiceOffer offer = new ServiceOffer();
        offer.setRequestId(saved.getId());
        offer.setPosition(1);
        offer.setStatus(OfferStatus.SELECTED);
        offer.setTariffType(TariffType.STANDARD);
        offer.setSpeedMbps(payload.getRequestedSpeedMbps());
        offer.setContractType(payload.getContractPreference() == null
                ? ContractType.OFFER
                : ContractType.valueOf(payload.getContractPreference().getValue()));
        ServiceOffer savedOffer = serviceOfferRepository.save(offer);

        saved.setSelectedOfferId(savedOffer.getId());
        requestRepository.saveAndFlush(saved);

        auditService.record(actor, companyId, saved.getId(), AuditAction.REQUEST_CREATED,
                "REQUEST", saved.getId(), null, RequestStatus.DRAFT.name(), null, null);

        RequestView view = viewFactory.toView(saved, actor, List.of());
        idempotencyService.store(actor.id(), idempotencyKey, CREATE_ENDPOINT, payload, 201, view);
        return view;
    }

    @Transactional
    public RequestView updateDraft(UUID requestId, UpdateRequestPayload payload) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.CLIENT_OWNER, Role.CLIENT_REQUESTER);

        Request request = loadExisting(requestId);
        accessPolicy.assertCanView(request, actor);
        requireAuthor(request, actor);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.illegalTransition("Изменять можно только черновик");
        }
        requireVersion(request, payload.getVersion());

        applyDraftFields(request, payload);
        ServiceOffer offer = selectedOffer(request);
        if (payload.getRequestedSpeedMbps() != null) {
            offer.setSpeedMbps(payload.getRequestedSpeedMbps());
        }
        if (payload.getContractPreference() != null) {
            offer.setContractType(ContractType.valueOf(payload.getContractPreference().getValue()));
        }
        serviceOfferRepository.save(offer);
        Request saved = requestRepository.saveAndFlush(request);

        auditService.record(actor, saved.getCompanyId(), saved.getId(), AuditAction.REQUEST_UPDATED,
                "REQUEST", saved.getId(), RequestStatus.DRAFT.name(), RequestStatus.DRAFT.name(),
                "Черновик изменён", null);

        return viewFactory.toView(saved, actor, List.of());
    }

    @Transactional
    public void deleteDraft(UUID requestId, long version) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.CLIENT_OWNER, Role.CLIENT_REQUESTER);

        Request request = loadExisting(requestId);
        accessPolicy.assertCanView(request, actor);
        requireAuthor(request, actor);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw ApiException.illegalTransition("Удалить можно только неотправленный черновик");
        }
        requireVersion(request, version);

        request.setDeletedAt(OffsetDateTime.now(clock));
        requestRepository.save(request);

        auditService.record(actor, request.getCompanyId(), request.getId(), AuditAction.REQUEST_DELETED,
                "REQUEST", request.getId(), RequestStatus.DRAFT.name(), null, null, null);
    }

    @Transactional(readOnly = true)
    public RequestView get(UUID requestId) {
        CurrentUser actor = currentUserProvider.current();
        Request request = loadExisting(requestId);
        accessPolicy.assertCanView(request, actor);
        return viewFactory.toView(request, actor, List.of());
    }

    @Transactional(readOnly = true)
    public RequestPage list(RequestStatus status, UUID companyId, UUID managerId, Boolean unassigned,
                            int page, int size, String sort) {
        CurrentUser actor = currentUserProvider.current();
        Pageable pageable = pageable(page, size, sort);
        Specification<Request> specification = accessPolicy.visibleTo(actor);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (companyId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
        }
        if (managerId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("managerId"), managerId));
        }
        if (Boolean.TRUE.equals(unassigned)) {
            specification = specification.and((root, query, cb) -> cb.isNull(root.get("managerId")));
        }

        Page<Request> result = requestRepository.findAll(specification, pageable);
        PageInfo pageInfo = new PageInfo()
                .number(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages());

        return new RequestPage()
                .page(pageInfo)
                .content(viewFactory.toViews(result.getContent(), actor));
    }

    @Transactional(readOnly = true)
    public AuditEntryPage history(UUID requestId, int page, int size) {
        CurrentUser actor = currentUserProvider.current();
        Request request = loadExisting(requestId);
        accessPolicy.assertCanView(request, actor);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<AuditLog> result = auditLogRepository.findByRequestIdOrderByCreatedAtAsc(requestId, pageable);

        PageInfo pageInfo = new PageInfo()
                .number(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages());

        return new AuditEntryPage()
                .page(pageInfo)
                .content(result.getContent().stream().map(this::toAuditEntry).toList());
    }

    private AuditEntry toAuditEntry(AuditLog log) {
        return new AuditEntry()
                .id(log.getId())
                .createdAt(log.getCreatedAt())
                .correlationId(log.getCorrelationId())
                .actorId(log.getActorId())
                .actorRole(ApiEnums.role(log.getActorRole()))
                .companyId(log.getCompanyId())
                .requestId(log.getRequestId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .comment(log.getComment())
                .details(log.getDetails());
    }

    private Request loadExisting(UUID requestId) {
        return requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private static void requireAuthor(Request request, CurrentUser actor) {
        if (!request.getCreatedBy().equals(actor.id())) {
            throw ApiException.accessDenied("Изменять заявку может только её автор");
        }
    }

    static void requireVersion(Request request, long expectedVersion) {
        if (request.getVersion() != expectedVersion) {
            throw ApiException.versionConflict(
                    "Версия заявки устарела: актуальная версия " + request.getVersion());
        }
    }

    private ServiceOffer selectedOffer(Request request) {
        if (request.getSelectedOfferId() == null) {
            throw new ApiException(ErrorCode.ILLEGAL_TRANSITION, "У заявки нет выбранного предложения");
        }
        return serviceOfferRepository.findById(request.getSelectedOfferId())
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_TRANSITION,
                        "Выбранное предложение не найдено"));
    }

    private static void applyDraftFields(Request request, CreateRequestPayload payload) {
        if (payload.getAddress() != null) {
            request.setAddress(payload.getAddress().trim());
        }
        if (payload.getRequestedSpeedMbps() != null) {
            request.setRequestedSpeedMbps(payload.getRequestedSpeedMbps());
        }
        if (payload.getDesiredDate() != null) {
            request.setDesiredDate(payload.getDesiredDate());
        }
        if (payload.getContactName() != null) {
            request.setContactName(payload.getContactName().trim());
        }
        if (payload.getContactPhone() != null) {
            request.setContactPhone(payload.getContactPhone().trim());
        }
        if (payload.getContractPreference() != null) {
            request.setContractPreference(ContractType.valueOf(payload.getContractPreference().getValue()));
        }
        if (payload.getClientComment() != null) {
            request.setClientComment(payload.getClientComment());
        }
    }

    private static void applyDraftFields(Request request, UpdateRequestPayload payload) {
        if (payload.getAddress() != null) {
            request.setAddress(payload.getAddress().trim());
        }
        if (payload.getRequestedSpeedMbps() != null) {
            request.setRequestedSpeedMbps(payload.getRequestedSpeedMbps());
        }
        if (payload.getDesiredDate() != null) {
            request.setDesiredDate(payload.getDesiredDate());
        }
        if (payload.getContactName() != null) {
            request.setContactName(payload.getContactName().trim());
        }
        if (payload.getContactPhone() != null) {
            request.setContactPhone(payload.getContactPhone().trim());
        }
        if (payload.getContractPreference() != null) {
            request.setContractPreference(ContractType.valueOf(payload.getContractPreference().getValue()));
        }
        if (payload.getClientComment() != null) {
            request.setClientComment(payload.getClientComment());
        }
    }

    private static Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validation("Некорректная пагинация",
                    List.of(new Violation("page", "INVALID", "page >= 0, size от 1 до 100")));
        }
        String property = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            property = parts[0].trim();
            if (!SORT_FIELDS.contains(property)) {
                throw ApiException.validation("Недопустимое поле сортировки",
                        List.of(new Violation("sort", "INVALID", "Допустимые поля: " + SORT_FIELDS)));
            }
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim().toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw ApiException.validation("Недопустимое направление сортировки",
                            List.of(new Violation("sort", "INVALID", "Допустимы: asc, desc")));
                }
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
