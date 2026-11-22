package com.b2borderflow.core.notification;

import com.b2borderflow.core.api.NotificationsApi;
import com.b2borderflow.core.api.model.NotificationPage;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой уведомлений (api-contract.md, 6.9). */
@RestController
public class NotificationsController implements NotificationsApi {

    private final NotificationService notificationService;

    public NotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResponseEntity<NotificationPage> listNotifications(Boolean unread, Integer page,
                                                              Integer size, String sort) {
        return ResponseEntity.ok(notificationService.list(unread, page, size, sort));
    }

    @Override
    public ResponseEntity<Void> readNotification(UUID notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> readAllNotifications() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
