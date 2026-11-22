package com.b2borderflow.core.notification;

import com.b2borderflow.core.api.model.WsTicketRedeemView;
import com.b2borderflow.core.api.model.WsTicketView;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Одноразовые билеты WebSocket (docs/system/api-contract.md, 4.4).
 * В БД хранится только SHA-256 билета; сырой билет выдаётся один раз.
 */
@Service
public class WsTicketService {

    static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final WsTicketRepository ticketRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(WsTicketRepository ticketRepository,
                           CurrentUserProvider currentUserProvider,
                           Clock clock) {
        this.ticketRepository = ticketRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public WsTicketView issue() {
        CurrentUser actor = currentUserProvider.current();
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String ticket = "wst_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        WsTicket entity = new WsTicket();
        entity.setTicketHash(hash(ticket));
        entity.setUserId(actor.id());
        entity.setActiveRole(actor.effectiveActiveRole());
        entity.setExpiresAt(OffsetDateTime.now(clock).plus(TICKET_TTL));
        ticketRepository.save(entity);

        return new WsTicketView()
                .ticket(ticket)
                .expiresIn((int) TICKET_TTL.toSeconds());
    }

    @Transactional
    public WsTicketRedeemView redeem(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw invalidTicket();
        }
        WsTicket ticket = ticketRepository.findByTicketHash(hash(rawTicket.trim()))
                .orElseThrow(WsTicketService::invalidTicket);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.getUsedAt() != null || !ticket.getExpiresAt().isAfter(now)) {
            throw invalidTicket();
        }
        ticket.setUsedAt(now);
        ticketRepository.save(ticket);
        return new WsTicketRedeemView()
                .userId(ticket.getUserId())
                .activeRole(ApiEnums.role(ticket.getActiveRole()));
    }

    private static ApiException invalidTicket() {
        return new ApiException(ErrorCode.UNAUTHORIZED, "Билет WebSocket недействителен");
    }

    private static String hash(String ticket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 недоступен", ex);
        }
    }
}
