package com.b2borderflow.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.support.WeekTwoApiSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Канал событий BFF: защита X-Internal-Token и сохранение published_at. */
class EventStreamIntegrationTest extends WeekTwoApiSupport {

    @Autowired
    private EventStreamService eventStreamService;

    @Test
    void subscribeMarksReplayedNotificationsPublished() {
        Flow flow = bootstrapWithManager("SMB");
        techCheckStage(flow);

        UUID notificationId = jdbc.queryForObject(
                "select id from notification where recipient_user_id = ? and published_at is null "
                        + "order by created_at limit 1",
                UUID.class, flow.managerId());

        SseEmitter emitter = eventStreamService.subscribe(null);
        assertThat(emitter).isNotNull();
        assertThat(jdbc.queryForObject(
                "select published_at is not null from notification where id = ?",
                Boolean.class, notificationId)).isTrue();
        emitter.complete();
    }

    @Test
    void endpointRejectsRequestWithoutInternalToken() {
        ApiResponse rejected = request(HttpMethod.GET, "/internal/v1/events/stream",
                new HttpHeaders(), null);
        assertThat(rejected.status()).isEqualTo(401);
        assertThat(rejected.body().get("code")).isEqualTo("UNAUTHORIZED");
    }
}
