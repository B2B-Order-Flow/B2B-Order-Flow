package com.b2borderflow.core.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.WeekTwoApiSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Ручные паузы координатора: pause, resume, extend-pause (api-contract.md, 10.10). */
class StageSlaIntegrationTest extends WeekTwoApiSupport {

    @Test
    void coordinatorPausesResumesAndExtendsStageSla() {
        Flow flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);

        ApiResponse paused = post("/internal/v1/stages/" + stageId + "/sla/pause",
                flow.coordinatorToken(), Role.COORDINATOR,
                Map.of("reasonCode", "AWAIT_CLIENT_DATA", "comment", "Ждём данные клиента",
                        "version", 0));
        assertStatus(paused, 200);
        assertThat(jdbc.queryForObject(
                "select status from sla_timer where stage_id = ?", String.class, stageId))
                .isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject(
                "select count(*) from sla_pause where timer_id = "
                        + "(select id from sla_timer where stage_id = ?) and ended_at is null",
                Integer.class, stageId)).isEqualTo(1);

        ApiResponse resumed = post("/internal/v1/stages/" + stageId + "/sla/resume",
                flow.coordinatorToken(), Role.COORDINATOR, Map.of("version", 0));
        assertStatus(resumed, 200);
        assertThat(jdbc.queryForObject(
                "select status from sla_timer where stage_id = ?", String.class, stageId))
                .isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                "select count(*) from sla_pause where timer_id = "
                        + "(select id from sla_timer where stage_id = ?) and ended_at is not null",
                Integer.class, stageId)).isEqualTo(1);

        post("/internal/v1/stages/" + stageId + "/sla/pause",
                flow.coordinatorToken(), Role.COORDINATOR,
                Map.of("reasonCode", "NO_ACCESS", "comment", "Нет доступа на объект",
                        "version", 0));
        ApiResponse extended = post("/internal/v1/stages/" + stageId + "/sla/extend-pause",
                flow.coordinatorToken(), Role.COORDINATOR,
                Map.of("comment", "Клиент просил ещё день", "version", 0));
        assertStatus(extended, 200);
        assertThat(jdbc.queryForObject(
                "select extended_count from sla_pause where timer_id = "
                        + "(select id from sla_timer where stage_id = ?) and ended_at is null",
                Integer.class, stageId)).isEqualTo(1);

        assertThat(auditCount(flow, "SLA_PAUSE_SET")).isEqualTo(2);
        assertThat(auditCount(flow, "SLA_PAUSE_RESUMED")).isEqualTo(1);
        assertThat(auditCount(flow, "SLA_PAUSE_EXTENDED")).isEqualTo(1);
    }

    @Test
    void automaticPauseCannotBeResumedByCoordinator() {
        Flow flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        UUID timerId = jdbc.queryForObject(
                "select id from sla_timer where stage_id = ?", UUID.class, stageId);
        jdbc.update("""
                        insert into sla_pause
                            (timer_id, reason_code, set_by, comment, started_at, limit_at, extended_count)
                        values (?, 'AWAIT_CLIENT_SIGNATURE', 'SYSTEM', null, now(), now() + interval '1 day', 0)
                        """, timerId);
        jdbc.update("update sla_timer set status = 'PAUSED' where id = ?", timerId);

        ApiResponse response = post("/internal/v1/stages/" + stageId + "/sla/resume",
                flow.coordinatorToken(), Role.COORDINATOR, Map.of("version", 0));

        assertStatus(response, 422);
        assertThat(response.body().get("code")).isEqualTo("PAUSE_REASON_INVALID");
    }

    @Test
    void automaticReasonAndNonCoordinatorAreRejected() {
        Flow flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);

        ApiResponse automatic = post("/internal/v1/stages/" + stageId + "/sla/pause",
                flow.coordinatorToken(), Role.COORDINATOR,
                Map.of("reasonCode", "AWAIT_CLIENT_SIGNATURE", "comment", "Нельзя", "version", 0));
        assertStatus(automatic, 422);
        assertThat(automatic.body().get("code")).isEqualTo("PAUSE_REASON_INVALID");

        ApiResponse tech = post("/internal/v1/stages/" + stageId + "/sla/pause",
                flow.techToken(), Role.TECH_SPECIALIST,
                Map.of("reasonCode", "NO_ACCESS", "comment", "Нет доступа", "version", 0));
        assertStatus(tech, 403);
        assertThat(tech.body().get("code")).isEqualTo("ACCESS_DENIED");
    }

    private int auditCount(Flow flow, String action) {
        return jdbc.queryForObject(
                "select count(*) from audit_log where request_id = ? and action = ?",
                Integer.class, flow.requestId(), action);
    }
}
