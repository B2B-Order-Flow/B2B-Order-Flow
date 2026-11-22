package com.b2borderflow.core.notification;

import com.b2borderflow.core.api.model.NotificationEventType;
import com.b2borderflow.core.api.model.NotificationPage;
import com.b2borderflow.core.api.model.NotificationView;
import com.b2borderflow.core.api.model.PageInfo;
import com.b2borderflow.core.company.OperatorRole;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final EventStreamService eventStreamService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               OperatorRoleRepository operatorRoleRepository,
                               AppUserRepository userRepository,
                               EventStreamService eventStreamService,
                               CurrentUserProvider currentUserProvider,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.userRepository = userRepository;
        this.eventStreamService = eventStreamService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public void notifyUsers(Collection<UUID> recipients, Request request, String eventType,
                            Map<String, Object> payload) {
        Set<UUID> distinct = new LinkedHashSet<>();
        recipients.stream().filter(java.util.Objects::nonNull).forEach(distinct::add);
        List<Notification> created = new java.util.ArrayList<>();
        for (UUID recipient : distinct) {
            Notification notification = new Notification();
            notification.setRecipientUserId(recipient);
            notification.setRequestId(request.getId());
            notification.setEventType(eventType);
            notification.setPayload(payload == null ? Map.of() : Map.copyOf(payload));
            created.add(notificationRepository.save(notification));
        }
        eventStreamService.publishAfterCommit(created);
    }

    /** Автор заявки и её менеджер — обычный набор получателей события заявки. */
    @Transactional
    public void notifyAuthorAndManager(Request request, String eventType) {
        notifyUsers(authorAndManager(request), request, eventType, Map.of());
    }

    /** Очередь роли: все активные сотрудники оператора с этой ролью. */
    @Transactional
    public void notifyQueue(RequestStage stage, Request request) {
        notifyQueue(stage, request, NotificationEvent.STAGE_QUEUED, Map.of());
    }

    @Transactional
    public void notifyQueue(RequestStage stage, Request request, String eventType,
                            Map<String, Object> payload) {
        if (stage.getQueueRole() == null) {
            return;
        }
        Map<String, Object> body = new java.util.HashMap<>(payload);
        body.put("stageId", stage.getId().toString());
        body.put("stageType", stage.getType().name());
        notifyUsers(activeUsersWithRole(stage.getQueueRole()), request, eventType, body);
    }

    @Transactional
    public void notifyAssignee(RequestStage stage, Request request) {
        notifyAssignee(stage, request, NotificationEvent.STAGE_ASSIGNED, Map.of());
    }

    @Transactional
    public void notifyAssignee(RequestStage stage, Request request, String eventType,
                              Map<String, Object> payload) {
        if (stage.getAssigneeId() == null) {
            return;
        }
        Map<String, Object> body = new java.util.HashMap<>(payload);
        body.put("stageId", stage.getId().toString());
        body.put("stageType", stage.getType().name());
        notifyUsers(Set.of(stage.getAssigneeId()), request, eventType, body);
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

    /** Уведомления SLA этапа: исполнитель (или очередь роли) и координатор. */
    @Transactional
    public void notifyAssigneeOrQueueAndCoordinators(RequestStage stage, Request request,
                                                     String eventType, Map<String, Object> payload) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (stage.getAssigneeId() != null) {
            recipients.add(stage.getAssigneeId());
        } else if (stage.getQueueRole() != null) {
            recipients.addAll(activeUsersWithRole(stage.getQueueRole()));
        }
        recipients.addAll(activeUsersWithRole(Role.COORDINATOR));
        notifyUsers(recipients, request, eventType, payload);
    }

    @Transactional
    public void notifyCoordinators(Request request, String eventType, Map<String, Object> payload) {
        notifyUsers(activeUsersWithRole(Role.COORDINATOR), request, eventType, payload);
    }

    @Transactional
    public void notifyCoordinatorsAndManager(Request request, String eventType, Map<String, Object> payload) {
        Set<UUID> recipients = new LinkedHashSet<>(activeUsersWithRole(Role.COORDINATOR));
        if (request.getManagerId() != null) {
            recipients.add(request.getManagerId());
        }
        notifyUsers(recipients, request, eventType, payload);
    }

    @Transactional
    public void notifyManager(Request request, String eventType, Map<String, Object> payload) {
        if (request.getManagerId() != null) {
            notifyUsers(Set.of(request.getManagerId()), request, eventType, payload);
        }
    }

    @Transactional
    public void notifyAuthor(Request request, String eventType, Map<String, Object> payload) {
        if (request.getCreatedBy() != null) {
            notifyUsers(Set.of(request.getCreatedBy()), request, eventType, payload);
        }
    }

    @Transactional
    public void notifyRoles(Set<Role> roles, Request request, String eventType, Map<String, Object> payload) {
        Set<UUID> recipients = new LinkedHashSet<>();
        for (Role role : roles) {
            recipients.addAll(activeUsersWithRole(role));
        }
        notifyUsers(recipients, request, eventType, payload);
    }

    // --- чтение и прочтение (api-contract.md, 6.9) ---

    @Transactional(readOnly = true)
    public NotificationPage list(Boolean unread, int page, int size, String sort) {
        CurrentUser actor = currentUserProvider.current();
        PageRequest pageable = pageable(page, size, sort);
        Page<Notification> result = Boolean.TRUE.equals(unread)
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        actor.id(), pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(actor.id(), pageable);

        List<NotificationView> content = result.getContent().stream()
                .map(this::toView)
                .toList();
        return new NotificationPage()
                .content(content)
                .page(new PageInfo()
                        .number(result.getNumber())
                        .size(result.getSize())
                        .totalElements(result.getTotalElements())
                        .totalPages(result.getTotalPages()));
    }

    @Transactional
    public void markRead(UUID notificationId) {
        CurrentUser actor = currentUserProvider.current();
        Notification notification = notificationRepository
                .findByIdAndRecipientUserId(notificationId, actor.id())
                .orElseThrow(() -> ApiException.notFound("Уведомление не найдено"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now(clock));
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(currentUserProvider.current().id(), OffsetDateTime.now(clock));
    }

    private NotificationView toView(Notification notification) {
        NotificationEventType eventType;
        try {
            eventType = NotificationEventType.fromValue(notification.getEventType());
        } catch (IllegalArgumentException ex) {
            eventType = null;
        }
        return new NotificationView()
                .id(notification.getId())
                .eventType(eventType)
                .requestId(notification.getRequestId())
                .payload(notification.getPayload() == null ? Map.of() : notification.getPayload())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .publishedAt(notification.getPublishedAt());
    }

    private static PageRequest pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validation("Некорректная пагинация",
                    List.of(new Violation("page", "INVALID", "page >= 0, size от 1 до 100")));
        }
        String property = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            property = parts[0].trim();
            if (!"createdAt".equals(property)) {
                throw ApiException.validation("Недопустимое поле сортировки",
                        List.of(new Violation("sort", "INVALID", "Допустимое поле: createdAt")));
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
