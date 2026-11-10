package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.api.model.ServiceOfferView;
import com.b2borderflow.core.api.model.Warning;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Сборка {@link RequestView}. Клиентская версия карточки не содержит исполнителей,
 * внутренних сумм и служебных полей (roles.md, 6.5, сноска 11).
 */
@Component
public class RequestViewFactory {

    private final ServiceOfferRepository serviceOfferRepository;
    private final CompanyRepository companyRepository;

    public RequestViewFactory(ServiceOfferRepository serviceOfferRepository,
                              CompanyRepository companyRepository) {
        this.serviceOfferRepository = serviceOfferRepository;
        this.companyRepository = companyRepository;
    }

    public RequestView toView(Request request, CurrentUser viewer, List<Warning> warnings) {
        Company company = companyRepository.findById(request.getCompanyId()).orElse(null);
        List<ServiceOffer> offers = serviceOfferRepository.findByRequestIdOrderByPositionAsc(request.getId());
        return buildView(request, viewer, offers, company, warnings);
    }

    public List<RequestView> toViews(List<Request> requests, CurrentUser viewer) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<UUID> requestIds = requests.stream().map(Request::getId).toList();
        Map<UUID, List<ServiceOffer>> offersByRequest = serviceOfferRepository
                .findByRequestIdInOrderByRequestIdAscPositionAsc(requestIds)
                .stream()
                .collect(Collectors.groupingBy(ServiceOffer::getRequestId));

        Map<UUID, Company> companiesById = companyRepository
                .findAllById(requests.stream().map(Request::getCompanyId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Company::getId, company -> company));

        List<RequestView> views = new ArrayList<>(requests.size());
        for (Request request : requests) {
            views.add(buildView(
                    request,
                    viewer,
                    offersByRequest.getOrDefault(request.getId(), List.of()),
                    companiesById.get(request.getCompanyId()),
                    List.of()));
        }
        return views;
    }

    private RequestView buildView(Request request, CurrentUser viewer, Collection<ServiceOffer> offers,
                                  Company company, List<Warning> warnings) {
        boolean clientView = isClientView(viewer);

        List<ServiceOfferView> offerViews = offers.stream()
                .map(this::toOfferView)
                .toList();

        RequestView view = new RequestView()
                .id(request.getId())
                .number(request.getNumber())
                .companyId(request.getCompanyId())
                .companyName(company == null ? null : company.getName())
                .status(ApiEnums.requestStatus(request.getStatus()))
                .previousStatus(ApiEnums.requestStatus(request.getPreviousStatus()))
                .statusChangedAt(request.getStatusChangedAt())
                .integrationError(request.isIntegrationError())
                .commitmentLevel((int) request.getCommitmentLevel())
                .version(request.getVersion())
                .createdBy(request.getCreatedBy())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .submittedAt(request.getSubmittedAt())
                .address(request.getAddress())
                .requestedSpeedMbps(request.getRequestedSpeedMbps())
                .desiredDate(request.getDesiredDate())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contractPreference(ApiEnums.contractType(request.getContractPreference()))
                .clientComment(request.getClientComment())
                .segment(request.getSegment() == null ? null : request.getSegment().name())
                .isGovCustomer(request.getGovCustomer())
                .selectedOfferId(request.getSelectedOfferId())
                .offers(offerViews)
                .warnings(warnings == null ? List.of() : warnings);

        if (!clientView) {
            view.managerId(request.getManagerId())
                    .crmManagerId(request.getCrmManagerId())
                    .debtAmount(money(request.getDebtAmount()));
        }

        return view;
    }

    private ServiceOfferView toOfferView(ServiceOffer offer) {
        return new ServiceOfferView()
                .id(offer.getId())
                .position(offer.getPosition())
                .status(ApiEnums.offerStatus(offer.getStatus()))
                .tariffType(ApiEnums.tariffType(offer.getTariffType()))
                .speedMbps(offer.getSpeedMbps())
                .discountPercent(money(offer.getDiscountPercent()))
                .priceRub(money(offer.getPriceRub()))
                .termMonths(offer.getTermMonths())
                .contractType(ApiEnums.contractType(offer.getContractType()));
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /** Клиент видит клиентскую версию карточки, даже если у него есть и операторская роль. */
    private static boolean isClientView(CurrentUser viewer) {
        Role active = viewer.activeRole();
        if (active != null) {
            return active.isClientRole();
        }
        return !viewer.roles().isEmpty() && viewer.roles().stream().allMatch(Role::isClientRole);
    }

    /** Предупреждение о сбое интеграции для ответа на отправку (EC-10). */
    public static Warning integrationWarning() {
        return new Warning()
                .code("EXTERNAL_SYSTEM_UNAVAILABLE")
                .message("Данные уточняются: внешняя система не ответила. Заявка сохранена, проверка будет повторена.");
    }

    /** Служебная карта для передачи в аудит/ревизию без лишних зависимостей. */
    public static Map<String, Object> snapshot(Request request) {
        Map<String, Object> snapshot = new HashMap<>();
        putIfNotNull(snapshot, "address", request.getAddress());
        putIfNotNull(snapshot, "requestedSpeedMbps", request.getRequestedSpeedMbps());
        putIfNotNull(snapshot, "desiredDate",
                request.getDesiredDate() == null ? null : request.getDesiredDate().toString());
        putIfNotNull(snapshot, "contactName", request.getContactName());
        putIfNotNull(snapshot, "contactPhone", request.getContactPhone());
        putIfNotNull(snapshot, "contractPreference",
                request.getContractPreference() == null ? null : request.getContractPreference().name());
        putIfNotNull(snapshot, "clientComment", request.getClientComment());
        return snapshot;
    }

    /** JSONB не любит лишние null: в снимке остаются только заполненные поля. */
    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
