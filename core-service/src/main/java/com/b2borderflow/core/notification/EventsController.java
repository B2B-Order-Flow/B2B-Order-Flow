package com.b2borderflow.core.notification;

import com.b2borderflow.core.api.EventsApi;
import com.b2borderflow.core.api.model.WsTicketRedeemPayload;
import com.b2borderflow.core.api.model.WsTicketRedeemView;
import com.b2borderflow.core.api.model.WsTicketView;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * HTTP-слой канала событий и билета WebSocket.
 *
 * <p>SSE-метод намеренно не реализует {@link EventsApi}: генератор описывает
 * ответ как {@code ResponseEntity<EventSignal>}, а поток отдаётся через
 * {@link SseEmitter}. Остальные операции ссылаются на константы путей контракта.</p>
 */
@RestController
public class EventsController {

    private final EventStreamService eventStreamService;
    private final WsTicketService ticketService;

    public EventsController(EventStreamService eventStreamService, WsTicketService ticketService) {
        this.eventStreamService = eventStreamService;
        this.ticketService = ticketService;
    }

    @GetMapping(path = EventsApi.PATH_STREAM_EVENTS, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @RequestHeader(value = "Last-Event-ID", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime lastEventId) {
        return eventStreamService.subscribe(lastEventId);
    }

    @GetMapping(EventsApi.PATH_ISSUE_WS_TICKET)
    public ResponseEntity<WsTicketView> issueWsTicket() {
        return ResponseEntity.ok(ticketService.issue());
    }

    @PostMapping(EventsApi.PATH_REDEEM_WS_TICKET)
    public ResponseEntity<WsTicketRedeemView> redeemWsTicket(
            @RequestBody WsTicketRedeemPayload payload) {
        return ResponseEntity.ok(ticketService.redeem(payload.getTicket()));
    }
}
