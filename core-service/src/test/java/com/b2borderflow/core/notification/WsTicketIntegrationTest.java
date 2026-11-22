package com.b2borderflow.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Билет WebSocket: одноразовость, срок и привязка к роли (api-contract.md, 4.4). */
class WsTicketIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ticketIsIssuedBoundToUserAndCanBeRedeemedOnce() {
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        String token = login("sales.manager");

        ApiResponse issued = get("/internal/v1/ws/ticket", token, Role.SALES_MANAGER);
        assertThat(issued.status()).isEqualTo(200);
        String ticket = (String) issued.body().get("ticket");
        assertThat(ticket).startsWith("wst_");
        assertThat(issued.body().get("expiresIn")).isEqualTo(30);

        assertThat(jdbc.queryForObject(
                "select count(*) from ws_ticket where ticket_hash = ?", Integer.class, ticket)).isZero();

        ApiResponse redeemed = request(HttpMethod.POST, "/internal/v1/ws/ticket/redeem",
                internalHeaders(), Map.of("ticket", ticket));
        assertThat(redeemed.status()).isEqualTo(200);
        assertThat(redeemed.body().get("userId")).isEqualTo(
                jdbc.queryForObject("select id from app_user where login = 'sales.manager'", UUID.class).toString());
        assertThat(redeemed.body().get("activeRole")).isEqualTo("SALES_MANAGER");

        ApiResponse second = request(HttpMethod.POST, "/internal/v1/ws/ticket/redeem",
                internalHeaders(), Map.of("ticket", ticket));
        assertThat(second.status()).isEqualTo(401);
        assertThat(second.body().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void expiredTicketIsRejected() {
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        String token = login("sales.manager");
        ApiResponse issued = get("/internal/v1/ws/ticket", token, Role.SALES_MANAGER);
        String ticket = (String) issued.body().get("ticket");

        jdbc.update("update ws_ticket set expires_at = now() - interval '1 second' where used_at is null");

        ApiResponse redeemed = request(HttpMethod.POST, "/internal/v1/ws/ticket/redeem",
                internalHeaders(), Map.of("ticket", ticket));
        assertThat(redeemed.status()).isEqualTo(401);
    }
}
