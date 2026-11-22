package com.b2borderflow.core.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WsTicketRepository extends JpaRepository<WsTicket, UUID> {

    Optional<WsTicket> findByTicketHash(String ticketHash);

    List<WsTicket> findByExpiresAtBefore(OffsetDateTime moment);

    /**
     * Атомарно погашает билет: только один параллельный вызов увидит {@code 1}.
     * Условие {@code used_at is null and expires_at > now} проверяется в UPDATE
     * под блокировкой строки (api-contract.md, 4.4).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WsTicket t
               set t.usedAt = :now
             where t.ticketHash = :ticketHash
               and t.usedAt is null
               and t.expiresAt > :now
            """)
    int consume(@Param("ticketHash") String ticketHash, @Param("now") OffsetDateTime now);
}
