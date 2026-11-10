package com.b2borderflow.core.company;

import com.b2borderflow.core.api.model.CompanyDecisionRequest;
import com.b2borderflow.core.api.model.CompanyPage;
import com.b2borderflow.core.api.model.CompanyView;
import com.b2borderflow.core.api.model.PageInfo;
import com.b2borderflow.core.api.model.RegisterCompanyRequest;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.idempotency.IdempotencyService;
import com.b2borderflow.core.integration.ExternalCallResult;
import com.b2borderflow.core.integration.RegistryClient;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Регистрация компании, её подтверждение администратором и карточка компании.
 * Вызов справочника выполняется до транзакции создания: сеть не держит соединение с БД.
 */
@Service
public class CompanyService {

    private static final String REGISTER_ENDPOINT = "/internal/v1/companies";
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "status");

    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final CompanyMapper companyMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final InnValidator innValidator;
    private final RegistryClient registryClient;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CompanyService(CompanyRepository companyRepository,
                          CompanyMembershipRepository membershipRepository,
                          MembershipRoleRepository membershipRoleRepository,
                          CompanyMapper companyMapper,
                          CurrentUserProvider currentUserProvider,
                          AuditService auditService,
                          InnValidator innValidator,
                          RegistryClient registryClient,
                          IdempotencyService idempotencyService,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.companyMapper = companyMapper;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.innValidator = innValidator;
        this.registryClient = registryClient;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public CompanyView register(RegisterCompanyRequest request, UUID idempotencyKey) {
        CurrentUser actor = currentUserProvider.current();

        Optional<IdempotencyService.StoredResponse> stored =
                idempotencyService.find(actor.id(), idempotencyKey, REGISTER_ENDPOINT, request);
        if (stored.isPresent()) {
            return idempotencyService.convert(stored.get(), CompanyView.class);
        }

        String inn = request.getInn().trim();
        if (!innValidator.isValid(inn)) {
            throw ApiException.validation("Неверный ИНН",
                    List.of(new Violation("inn", "INVALID", "Проверьте длину и контрольную сумму ИНН")));
        }
        if (companyRepository.existsByInnAndStatus(inn, CompanyStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.COMPANY_INN_EXISTS,
                    "Активная компания с таким ИНН уже есть");
        }

        RegistryClient.RegistryData registry = fetchRegistry(inn);
        if (!registry.found()) {
            throw new ApiException(ErrorCode.COMPANY_INN_NOT_FOUND,
                    "ИНН не найден в справочнике клиентов");
        }
        if ("LIQUIDATED".equalsIgnoreCase(registry.status())) {
            throw ApiException.validation("Организация ликвидирована",
                    List.of(new Violation("inn", "LIQUIDATED", "Организация ликвидирована по данным справочника")));
        }

        return Objects.requireNonNull(
                transactionTemplate.execute(status -> createCompany(request, inn, actor, idempotencyKey)));
    }

    private CompanyView createCompany(RegisterCompanyRequest request, String inn,
                                      CurrentUser actor, UUID idempotencyKey) {
        Company company = new Company();
        company.setName(request.getName().trim());
        company.setInn(inn);
        company.setKpp(trimToNull(request.getKpp()));
        company.setOgrn(trimToNull(request.getOgrn()));
        company.setLegalAddress(trimToNull(request.getLegalAddress()));
        company.setStatus(CompanyStatus.PENDING);
        Company saved = companyRepository.saveAndFlush(company);

        OffsetDateTime now = OffsetDateTime.now(clock);
        CompanyMembership membership = new CompanyMembership();
        membership.setCompanyId(saved.getId());
        membership.setUserId(actor.id());
        membership.setAddedBy(actor.id());
        membership.setAddedAt(now);
        CompanyMembership savedMembership = membershipRepository.save(membership);

        MembershipRole ownerRole = new MembershipRole(
                new MembershipRoleId(savedMembership.getId(), Role.CLIENT_OWNER));
        membershipRoleRepository.save(ownerRole);

        auditService.record(actor, saved.getId(), null, AuditAction.COMPANY_REGISTERED,
                "COMPANY", saved.getId(), null, saved.getStatus().name(), null, null);

        CompanyView view = companyMapper.toView(saved);
        idempotencyService.store(actor.id(), idempotencyKey, REGISTER_ENDPOINT,
                request, 201, view);
        return view;
    }

    private RegistryClient.RegistryData fetchRegistry(String inn) {
        ExternalCallResult<RegistryClient.RegistryData> result = registryClient.fetch(inn);
        if (!result.ok()) {
            throw new ApiException(ErrorCode.EXTERNAL_SYSTEM_UNAVAILABLE,
                    "Справочник клиентов недоступен, повторите регистрацию позже");
        }
        return result.data();
    }

    @Transactional(readOnly = true)
    public CompanyView getCompany(UUID companyId) {
        CurrentUser actor = currentUserProvider.current();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Компания не найдена"));

        boolean admin = actor.hasRole(Role.ADMIN);
        boolean member = actor.belongsToCompany(companyId);
        if (!admin && !member) {
            throw ApiException.notFound("Компания не найдена");
        }
        return companyMapper.toView(company);
    }

    @Transactional(readOnly = true)
    public CompanyPage listForConfirmation(CompanyStatus status,
                                           int page, int size, String sort) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.ADMIN);

        Pageable pageable = pageable(page, size, sort);
        Page<Company> result = status == null
                ? companyRepository.findAll(pageable)
                : companyRepository.findByStatus(status, pageable);

        PageInfo pageInfo = new PageInfo()
                .number(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages());

        return new CompanyPage()
                .page(pageInfo)
                .content(result.getContent().stream().map(companyMapper::toView).toList());
    }

    @Transactional
    public CompanyView confirm(UUID companyId, CompanyDecisionRequest request) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.ADMIN);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Компания не найдена"));
        requirePending(company);

        if (companyRepository.existsByInnAndStatus(company.getInn(), CompanyStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.COMPANY_INN_EXISTS,
                    "Активная компания с таким ИНН уже есть");
        }

        company.setStatus(CompanyStatus.ACTIVE);
        company.setConfirmedBy(actor.id());
        company.setConfirmedAt(OffsetDateTime.now(clock));
        company.setRejectReason(null);
        Company saved = companyRepository.save(company);

        auditService.record(actor, saved.getId(), null, AuditAction.COMPANY_CONFIRMED,
                "COMPANY", saved.getId(), CompanyStatus.PENDING.name(), saved.getStatus().name(),
                request == null ? null : request.getComment(), null);

        return companyMapper.toView(saved);
    }

    @Transactional
    public CompanyView reject(UUID companyId, CompanyDecisionRequest request) {
        CurrentUser actor = currentUserProvider.current();
        actor.requireAnyRole(Role.ADMIN);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Компания не найдена"));
        requirePending(company);

        String reason = request == null ? null : request.getReason();
        if (reason == null || reason.isBlank()) {
            throw ApiException.validation("Причина отклонения обязательна",
                    List.of(new Violation("reason", "REQUIRED", "Укажите причину отклонения компании")));
        }

        company.setStatus(CompanyStatus.REJECTED);
        company.setRejectReason(reason.trim());
        company.setConfirmedBy(actor.id());
        company.setConfirmedAt(OffsetDateTime.now(clock));
        Company saved = companyRepository.save(company);

        auditService.record(actor, saved.getId(), null, AuditAction.COMPANY_REJECTED,
                "COMPANY", saved.getId(), CompanyStatus.PENDING.name(), saved.getStatus().name(),
                reason.trim(), null);

        return companyMapper.toView(saved);
    }

    private static void requirePending(Company company) {
        if (company.getStatus() != CompanyStatus.PENDING) {
            throw ApiException.illegalTransition("Компания уже рассмотрена");
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
                        List.of(new Violation("sort", "INVALID", "Допустимы: " + SORT_FIELDS)));
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

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
