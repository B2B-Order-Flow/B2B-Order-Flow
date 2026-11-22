package com.b2borderflow.core.notification;

import com.b2borderflow.core.api.model.EventSignal;
import com.b2borderflow.core.api.model.NotificationEventType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Канал событий ядро → BFF (docs/system/api-contract.md, 4.5).
 *
 * <p>Держит SSE-подписчиков шлюза. Новые уведомления отправляются после коммита
 * транзакции перехода. Если шлюз не подключён, {@code published_at} остаётся
 * пустым; при подключении сначала уходят неопубликованные записи и записи после
 * {@code Last-Event-ID}, затем новые.</p>
 */
@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);
    private static final int REPLAY_BATCH = 1000;

    private final NotificationRepository notificationRepository;
    private final NotificationPublishedService publishedService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public EventStreamService(NotificationRepository notificationRepository,
                              NotificationPublishedService publishedService) {
        this.notificationRepository = notificationRepository;
        this.publishedService = publishedService;
    }

    /** Новое SSE-подключение BFF. */
    public synchronized SseEmitter subscribe(OffsetDateTime lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        // Немедленный комментарий заставляет контейнер отдать заголовки text/event-stream,
        // не дожидаясь первого уведомления.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
            return emitter;
        }

        Set<Notification> replay = new LinkedHashSet<>();
        replay.addAll(notificationRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, REPLAY_BATCH)));
        if (lastEventId != null) {
            replay.addAll(notificationRepository.findByCreatedAtAfterOrderByCreatedAtAsc(
                    lastEventId, PageRequest.of(0, REPLAY_BATCH)));
        }
        for (Notification notification : replay) {
            if (!send(emitter, notification)) {
                break;
            }
            publishedService.markPublished(notification.getId());
        }
        return emitter;
    }

    /** Публикация после коммита транзакции; вызывается из {@link NotificationService}. */
    public void publishAfterCommit(Collection<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifications.forEach(this::publish);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifications.forEach(EventStreamService.this::publish);
            }
        });
    }

    private synchronized void publish(Notification notification) {
        if (emitters.isEmpty()) {
            // Шлюз не подключён: published_at останется пустым и уйдёт при следующем подключении.
            return;
        }
        EventSignal signal = signal(notification);
        if (signal == null) {
            return;
        }
        boolean sent = false;
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            if (send(emitter, signal)) {
                sent = true;
            }
        }
        if (sent) {
            publishedService.markPublished(notification.getId());
        }
    }

    private boolean send(SseEmitter emitter, Notification notification) {
        EventSignal signal = signal(notification);
        if (signal == null) {
            return false;
        }
        return send(emitter, signal);
    }

    private boolean send(SseEmitter emitter, EventSignal signal) {
        try {
            // Last-Event-ID — created_at, а не id: по нему BFF продолжает поток.
            emitter.send(SseEmitter.event()
                    .id(signal.getCreatedAt().toString())
                    .data(signal));
            return true;
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
            log.debug("SSE-соединение закрыто: {}", ex.getMessage());
            return false;
        }
    }

    static EventSignal signal(Notification notification) {
        NotificationEventType type;
        try {
            type = NotificationEventType.fromValue(notification.getEventType());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (type == null) {
            return null;
        }
        Map<String, Object> payload = notification.getPayload() == null
                ? java.util.Map.of()
                : notification.getPayload();
        // В сигнале компании по notifications.md, 5 — только companyId; причина отказа
        // остаётся в REST-уведомлении и не уходит в WebSocket.
        if (type == NotificationEventType.COMPANY_CONFIRMED
                || type == NotificationEventType.COMPANY_REJECTED) {
            Object companyId = payload.get("companyId");
            payload = companyId == null ? java.util.Map.of() : java.util.Map.of("companyId", companyId);
        }
        return new EventSignal()
                .id(notification.getId())
                .type(type)
                .requestId(notification.getRequestId())
                .userId(notification.getRecipientUserId())
                .createdAt(notification.getCreatedAt())
                .payload(payload);
    }
}
