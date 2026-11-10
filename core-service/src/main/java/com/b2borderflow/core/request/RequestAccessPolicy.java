package com.b2borderflow.core.request;

import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Единая политика доступа к заявкам. Её нельзя «забыть» в контроллере: любой
 * запрос к заявке сначала проходит через этот компонент (domain-model.md, 7).
 *
 * <p>Клиент видит только заявки своей компании; чужая заявка отдаётся как 404
 * и попытка пишется в аудит (EC-16). Менеджер видит свои заявки и очередь
 * нераспределённых; координатор и руководитель B2B — все.
 */
@Component
public class RequestAccessPolicy {

    private final ExternalCheckRepository externalCheckRepository;
    private final RequestStageRepository stageRepository;
    private final AuditService auditService;

    public RequestAccessPolicy(ExternalCheckRepository externalCheckRepository,
                               RequestStageRepository stageRepository,
                               AuditService auditService) {
        this.externalCheckRepository = externalCheckRepository;
        this.stageRepository = stageRepository;
        this.auditService = auditService;
    }

    public void assertCanView(Request request, CurrentUser user) {
        if (!canView(request, user)) {
            auditService.recordDeniedAccess(user, request.getCompanyId(), request.getId(),
                    "Пользователь не имеет доступа к заявке");
            throw ApiException.notFound("Заявка не найдена");
        }
    }

    public boolean canView(Request request, CurrentUser user) {
        if (request.isDeleted()) {
            return false;
        }
        Role role = user.effectiveActiveRole();

        if (role.isClientRole()) {
            return user.membership(request.getCompanyId())
                    .map(membership -> membership.roles().contains(role))
                    .orElse(false);
        }
        if (role == Role.ADMIN) {
            return false;
        }
        if (role == Role.COORDINATOR || role == Role.B2B_HEAD) {
            return true;
        }
        if (role == Role.SALES_MANAGER) {
            return user.id().equals(request.getManagerId()) || isInManagerQueue(request);
        }
        return stageRepository.existsByRequestIdAndAssigneeId(request.getId(), user.id())
                || stageRepository.existsByRequestIdAndQueueRoleAndStatus(
                        request.getId(), role, RequestStageStatus.QUEUED);
    }

    /** Заявка в очереди менеджеров: данные проверены, менеджера ещё нет. */
    public boolean isInManagerQueue(Request request) {
        return request.getStatus() == RequestStatus.DATA_CHECK
                && request.getManagerId() == null
                && !request.isIntegrationError()
                && externalCheckRepository.countSuccessfulSystems(request.getId()) == 3;
    }

    /**
     * Ограничение выборки для списка. Для клиента companyId подставляется из
     * членства, а не из фильтра запроса: иначе фильтром можно было бы увидеть чужое.
     */
    public Specification<Request> visibleTo(CurrentUser user) {
        Role role = user.effectiveActiveRole();
        if (role == Role.ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Администратор не работает с заявками");
        }

        return (root, query, criteriaBuilder) -> {
            Predicate notDeleted = criteriaBuilder.isNull(root.get("deletedAt"));

            if (role.isClientRole()) {
                UUID companyId = user.activeCompanyId();
                return criteriaBuilder.and(
                        notDeleted,
                        criteriaBuilder.equal(root.get("companyId"), companyId));
            }
            if (role == Role.COORDINATOR || role == Role.B2B_HEAD) {
                return notDeleted;
            }
            if (role == Role.SALES_MANAGER) {
                Predicate own = criteriaBuilder.equal(root.get("managerId"), user.id());
                Predicate queue = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("status"), RequestStatus.DATA_CHECK),
                        criteriaBuilder.isNull(root.get("managerId")),
                        criteriaBuilder.isFalse(root.get("integrationError")),
                        hasThreeSuccessfulChecks(root, query, criteriaBuilder));
                return criteriaBuilder.and(notDeleted, criteriaBuilder.or(own, queue));
            }

            Subquery<UUID> stageSubquery = query.subquery(UUID.class);
            Root<RequestStage> stage = stageSubquery.from(RequestStage.class);
            stageSubquery.select(stage.get("id")).where(criteriaBuilder.and(
                    criteriaBuilder.equal(stage.get("requestId"), root.get("id")),
                    criteriaBuilder.or(
                            criteriaBuilder.equal(stage.get("assigneeId"), user.id()),
                            criteriaBuilder.and(
                                    criteriaBuilder.equal(stage.get("queueRole"), role),
                                    criteriaBuilder.equal(stage.get("status"), RequestStageStatus.QUEUED)))));

            return criteriaBuilder.and(
                    notDeleted,
                    criteriaBuilder.or(
                            criteriaBuilder.equal(root.get("managerId"), user.id()),
                            criteriaBuilder.exists(stageSubquery)));
        };
    }

    private static Predicate hasThreeSuccessfulChecks(Root<Request> request,
                                                      jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                      jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        Subquery<Long> countSubquery = query.subquery(Long.class);
        Root<ExternalCheck> check = countSubquery.from(ExternalCheck.class);
        countSubquery.select(criteriaBuilder.countDistinct(check.get("system")))
                .where(
                        criteriaBuilder.equal(check.get("requestId"), request.get("id")),
                        criteriaBuilder.equal(check.get("status"), ExternalCheckStatus.OK));
        return criteriaBuilder.equal(countSubquery, 3L);
    }
}
