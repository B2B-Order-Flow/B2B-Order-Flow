package com.b2borderflow.core.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.WeekTwoApiSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-01 целиком: T01 → T18 по одному сценарию (plan-week2.md, результат недели). */
class Sc01EndToEndIntegrationTest extends WeekTwoApiSupport {

    @Test
    void sc01RunsFromSubmissionToClosing() {
        Flow flow = bootstrapWithManager("SMB");
        assertThat(flow.requestVersion()).isPositive();

        UUID techStage = techCheckStage(flow);
        long techStageVersion = claimStage(techStage, flow.techToken());
        ApiResponse techResult = post("/internal/v1/stages/" + techStage + "/tech-result",
                flow.techToken(), Role.TECH_SPECIALIST,
                Map.of("result", "AVAILABLE", "version", techStageVersion));
        assertStatus(techResult, 200);

        ApiResponse accepted = post("/internal/v1/requests/" + flow.requestId() + "/contract/accept-offer",
                flow.clientToken(), Role.CLIENT_OWNER,
                Map.of("version", currentRequestVersion(flow)));
        assertStatus(accepted, 200);
        assertThat(accepted.body().get("status")).isEqualTo("IN_EXECUTION");
        assertThat(accepted.body().get("contractConcludedAt")).isNotNull();

        UUID installation = activeStageId(flow, "INSTALLATION");
        ApiResponse installationClaimed = post("/internal/v1/stages/" + installation + "/claim",
                flow.engineerToken(), Role.ENGINEER, Map.of("version", 0));
        assertStatus(installationClaimed, 200);
        assertThat(jdbc.queryForObject(
                "select work_started_at is not null from request where id = ?",
                Boolean.class, flow.requestId())).isTrue();

        ApiResponse installationDone = post("/internal/v1/stages/" + installation + "/complete",
                flow.engineerToken(), Role.ENGINEER,
                Map.of("version", number(installationClaimed.body().get("version"))));
        assertStatus(installationDone, 200);

        UUID activation = activeStageId(flow, "ACTIVATION");
        ApiResponse activationClaimed = post("/internal/v1/stages/" + activation + "/claim",
                flow.engineerToken(), Role.ENGINEER, Map.of("version", 0));
        assertStatus(activationClaimed, 200);
        ApiResponse activated = post("/internal/v1/stages/" + activation + "/activate",
                flow.engineerToken(), Role.ENGINEER,
                Map.of("version", number(activationClaimed.body().get("version"))));
        assertStatus(activated, 200);

        ApiResponse actSent = post("/internal/v1/requests/" + flow.requestId() + "/act/sent",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", currentRequestVersion(flow)));
        assertStatus(actSent, 200);

        ApiResponse closed = post("/internal/v1/requests/" + flow.requestId() + "/act/signed",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", number(actSent.body().get("version"))));
        assertStatus(closed, 200);
        assertThat(closed.body().get("status")).isEqualTo("CLOSED");
        assertThat(closed.body().get("closedAt")).isNotNull();
        assertThat(closed.body().get("acceptedByDefault")).isEqualTo(false);

        List<String> actions = jdbc.queryForList(
                "select action from audit_log where request_id = ?", String.class, flow.requestId());
        assertThat(actions).contains(
                "REQUEST_SUBMITTED",
                "REQUEST_DATA_CHECK_COMPLETED",
                "REQUEST_MANAGER_ASSIGNED",
                "REQUEST_COMMERCIAL_TERMS_SET",
                "STAGE_CLAIMED",
                "REQUEST_TECH_RESULT_SET",
                "REQUEST_OFFER_ACCEPTED",
                "REQUEST_WORK_STARTED",
                "STAGE_COMPLETED",
                "STAGE_ACTIVATED",
                "REQUEST_ACT_SENT",
                "REQUEST_ACT_SIGNED");

        List<String> notifications = jdbc.queryForList(
                "select event_type from notification where request_id = ?", String.class, flow.requestId());
        assertThat(notifications).contains(
                "REQUEST_SUBMITTED", "REQUEST_ASSIGNED", "STAGE_QUEUED", "STATUS_CHANGED",
                "CONTRACT_READY", "SERVICE_ACTIVATED", "ACT_SENT", "REQUEST_CLOSED");
    }
}
