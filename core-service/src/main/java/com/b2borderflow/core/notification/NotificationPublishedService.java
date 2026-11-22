package com.b2borderflow.core.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отметка {@code notification.published_at} отдельной транзакцией.
 *
 * <p>Вызывается после успешной отправки SSE-сигнала, в том числе из
 * {@code afterCommit}: исходная транзакция уже завершена, и сохранять сущность
 * в её ресурсах нельзя. {@code REQUIRES_NEW} гарантирует собственный commit.</p>
 */
@Service
public class NotificationPublishedService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationPublishedService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID notificationId) {
        notificationRepository.findById(notificationId)
                .filter(notification -> notification.getPublishedAt() == null)
                .ifPresent(notification -> notification.setPublishedAt(OffsetDateTime.now(clock)));
    }
}