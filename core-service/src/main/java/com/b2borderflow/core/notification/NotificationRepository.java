package com.b2borderflow.core.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID recipientUserId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    /** Недопубликованные сигналы: отправляются первыми при подключении шлюза. */
    List<Notification> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    /** Сигналы после {@code Last-Event-ID}; идентификатор потока — {@code created_at}. */
    List<Notification> findByCreatedAtAfterOrderByCreatedAtAsc(OffsetDateTime createdAt, Pageable pageable);

    @Modifying
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.recipientUserId = :userId
               and n.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") OffsetDateTime readAt);
}
