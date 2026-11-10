package com.b2borderflow.core.audit;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.logging.CorrelationId;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.user.AppUser;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись в неизменяемый журнал. Вызывается в той же транзакции, что и изменение
 * заявки, чтобы статус и аудит коммитились вместе (status-model.md, принцип 3).
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public void record(CurrentUser actor, UUID companyId, UUID requestId, AuditAction action,
                       String entityType, UUID entityId, String fromStatus, String toStatus,
                       String comment, Map<String, Object> details) {
        recordInternal(actor.id(), actor.activeRole(), companyId, requestId, action,
                entityType, entityId, fromStatus, toStatus, comment, details);
    }

    public void recordForUser(AppUser user, Role role, UUID companyId, AuditAction action,
                              String entityType, UUID entityId, String fromStatus, String toStatus,
                              String comment, Map<String, Object> details) {
        recordInternal(user.getId(), role, companyId, null, action,
                entityType, entityId, fromStatus, toStatus, comment, details);
    }

    public void recordSystem(UUID companyId, UUID requestId, AuditAction action,
                             String entityType, UUID entityId, String fromStatus, String toStatus,
                             String comment, Map<String, Object> details) {
        recordInternal(null, null, companyId, requestId, action,
                entityType, entityId, fromStatus, toStatus, comment, details);
    }

    /**
     * Отказ в доступе (EC-16) пишется отдельной транзакцией: основная операция
     * завершится ошибкой и откатится, а факт попытки должен остаться.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeniedAccess(CurrentUser actor, UUID companyId, UUID requestId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        recordInternal(actor.id(), actor.activeRole(), companyId, requestId, AuditAction.ACCESS_DENIED,
                "REQUEST", requestId, null, null, null, details);
    }

    private void recordInternal(UUID actorId, Role actorRole, UUID companyId, UUID requestId,
                                AuditAction action, String entityType, UUID entityId,
                                String fromStatus, String toStatus, String comment,
                                Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        entry.setCreatedAt(OffsetDateTime.now(clock));
        entry.setCorrelationId(CorrelationId.current());
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setCompanyId(companyId);
        entry.setRequestId(requestId);
        entry.setAction(action.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setComment(comment);
        entry.setDetails(details == null ? Map.of() : new HashMap<>(details));
        auditLogRepository.save(entry);
    }
}
