package com.b2borderflow.core.notification;

import com.b2borderflow.core.company.OperatorRole;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись уведомлений в транзакции перехода (docs/system/notifications.md, раздел 6).
 * Получатели вычисляются в момент события; дублей на одного пользователя нет.
 * Доставка сигнала шлюзу и чтение по REST — следующая часть недели.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OperatorRoleRepository operatorRoleRepository;
    private final AppUserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               OperatorRoleRepository operatorRoleRepository,
                               AppUserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notifyUsers(Collection<UUID> recipients, Request request, String eventType,
                            Map<String, Object> payload) {
        Set<UUID> distinct = new LinkedHashSet<>();
        recipients.stream().filter(java.util.Objects::nonNull).forEach(distinct::add);
        for (UUID recipient : distinct) {
            Notification notification = new Notification();
            notification.setRecipientUserId(recipient);
            notification.setRequestId(request.getId());
            notification.setEventType(eventType);
            notification.setPayload(payload == null ? Map.of() : Map.copyOf(payload));
            notificationRepository.save(notification);
        }
    }

    /** Автор заявки и её менеджер — обычный набор получателей события заявки. */
    @Transactional
    public void notifyAuthorAndManager(Request request, String eventType) {
        notifyUsers(authorAndManager(request), request, eventType, Map.of());
    }

    /** Очередь роли: все активные сотрудники оператора с этой ролью. */
    @Transactional
    public void notifyQueue(RequestStage stage, Request request) {
        if (stage.getQueueRole() == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "stageId", stage.getId().toString(),
                "stageType", stage.getType().name());
        notifyUsers(activeUsersWithRole(stage.getQueueRole()), request, NotificationEvent.STAGE_QUEUED, payload);
    }

    @Transactional
    public void notifyAssignee(RequestStage stage, Request request) {
        if (stage.getAssigneeId() == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "stageId", stage.getId().toString(),
                "stageType", stage.getType().name());
        notifyUsers(Set.of(stage.getAssigneeId()), request, NotificationEvent.STAGE_ASSIGNED, payload);
    }

    public Set<UUID> authorAndManager(Request request) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (request.getCreatedBy() != null) {
            recipients.add(request.getCreatedBy());
        }
        if (request.getManagerId() != null) {
            recipients.add(request.getManagerId());
        }
        return recipients;
    }

    private Set<UUID> activeUsersWithRole(Role role) {
        Set<UUID> userIds = new LinkedHashSet<>();
        for (OperatorRole operatorRole : operatorRoleRepository.findByIdRole(role)) {
            userIds.add(operatorRole.getId().getUserId());
        }
        return userRepository.findAllById(userIds).stream()
                .filter(AppUser::isActive)
                .map(AppUser::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
