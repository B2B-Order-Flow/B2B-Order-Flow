package com.b2borderflow.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Канал событий BFF: подписка и защита X-Internal-Token (api-contract.md, 4.5). */
class EventStreamIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventStreamService eventStreamService;

    @Test
    void subscribeReturnsEmitterAndEndpointRejectsRequestWithoutInternalToken() {
        SseEmitter emitter = eventStreamService.subscribe(null);
        assertThat(emitter).isNotNull();
        emitter.complete();

        ApiResponse rejected = request(HttpMethod.GET, "/internal/v1/events/stream",
                new HttpHeaders(), null);
        assertThat(rejected.status()).isEqualTo(401);
        assertThat(rejected.body().get("code")).isEqualTo("UNAUTHORIZED");
    }
}
