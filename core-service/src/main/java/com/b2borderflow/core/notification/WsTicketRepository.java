package com.b2borderflow.core.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WsTicketRepository extends JpaRepository<WsTicket, UUID> {

    Optional<WsTicket> findByTicketHash(String ticketHash);

    List<WsTicket> findByExpiresAtBefore(OffsetDateTime moment);
}
