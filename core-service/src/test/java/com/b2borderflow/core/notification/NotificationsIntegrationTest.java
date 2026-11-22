package com.b2borderflow.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.WeekTwoApiSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GET /notifications, read, read-all (api-contract.md, 6.9). */
class NotificationsIntegrationTest extends WeekTwoApiSupport {

    @Test
    void managerReadsOwnNotificationsAndCannotReadForeign() {
        Flow flow = bootstrapWithManager("SMB");
        techCheckStage(flow); // T04 создаёт STATUS_CHANGED менеджеру и STAGE_QUEUED ТС

        ApiResponse unread = get("/internal/v1/notifications?unread=true&size=100",
                flow.managerToken(), Role.SALES_MANAGER);
        assertStatus(unread, 200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) unread.body().get("content");
        assertThat(content).isNotEmpty();
        Map<String, Object> own = content.stream()
                .filter(item -> "STATUS_CHANGED".equals(item.get("eventType")))
                .findFirst()
                .orElseThrow();
        UUID notificationId = UUID.fromString((String) own.get("id"));
        assertThat(own.get("readAt")).isNull();
        assertThat(own.get("requestNumber")).isEqualTo(
                jdbc.queryForObject("select number from request where id = ?",
                        String.class, flow.requestId()));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) own.get("payload");
        assertThat(payload.get("status")).isEqualTo("TECH_CHECK");

        ApiResponse read = request(org.springframework.http.HttpMethod.POST,
                "/internal/v1/notifications/" + notificationId + "/read",
                authorizedHeaders(flow.managerToken(), Role.SALES_MANAGER), null);
        assertStatus(read, 204);
        assertThat(jdbc.queryForObject(
                "select read_at is not null from notification where id = ?",
                Boolean.class, notificationId)).isTrue();

        ApiResponse repeat = request(org.springframework.http.HttpMethod.POST,
                "/internal/v1/notifications/" + notificationId + "/read",
                authorizedHeaders(flow.managerToken(), Role.SALES_MANAGER), null);
        assertStatus(repeat, 204);

        ApiResponse foreign = request(org.springframework.http.HttpMethod.POST,
                "/internal/v1/notifications/" + notificationId + "/read",
                authorizedHeaders(flow.techToken(), Role.TECH_SPECIALIST), null);
        assertStatus(foreign, 404);
        assertThat(foreign.body().get("code")).isEqualTo("NOT_FOUND");

        ApiResponse afterRead = get("/internal/v1/notifications?unread=true",
                flow.managerToken(), Role.SALES_MANAGER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) afterRead.body().get("content");
        assertThat(remaining)
                .extracting(item -> item.get("id"))
                .doesNotContain(notificationId.toString());
    }

    @Test
    void readAllMarksOnlyOwnUnreadNotifications() {
        Flow flow = bootstrapWithManager("SMB");
        techCheckStage(flow);

        ApiResponse readAll = request(org.springframework.http.HttpMethod.POST,
                "/internal/v1/notifications/read-all",
                authorizedHeaders(flow.managerToken(), Role.SALES_MANAGER), null);
        assertStatus(readAll, 204);

        assertThat(jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and read_at is null",
                Integer.class, flow.managerId())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and read_at is null",
                Integer.class, flow.techId())).isPositive();
    }
}
