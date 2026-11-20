package com.b2borderflow.core.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import com.b2borderflow.core.user.AppUser;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Вертикальный срез недели 2: T04 → TECH_CHECK → T07/T05 → T13 → T14 → T16 → T18
 * (status-model.md, 6.1 и 6.1.2; plan-week2.md, разделы 9.3 и 9.4).
 */
class WeekTwoWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String INN = "7725012341";

    @Test
    void commercialTermsBuildsPreliminaryRouteAndCreatesTechCheckStage() {
        Bootstrap flow = bootstrapWithManager("SMB");

        ApiResponse terms = setCommercialTerms(flow, Map.of(
                "tariffType", "STANDARD",
                "contractType", "OFFER",
                "version", flow.requestVersion()));

        assertThat(terms.status()).isEqualTo(200);
        assertThat(terms.body().get("status")).isEqualTo("TECH_CHECK");
        assertThat(terms.body().get("routeVersion")).isEqualTo(1);
        assertThat(terms.body().get("requestClass")).isEqualTo("STANDARD");

        Map<String, Object> stage = firstStage(terms);
        assertThat(stage.get("type")).isEqualTo("TECH_CHECK");
        assertThat(stage.get("status")).isEqualTo("QUEUED");
        assertThat(stage.get("queueRole")).isEqualTo("TECH_SPECIALIST");
        assertThat(stage.get("requiredByRules")).isEqualTo(List.of());

        UUID stageId = UUID.fromString((String) stage.get("id"));
        Map<String, Object> timer = jdbc.queryForMap(
                "select status, norm_minutes from sla_timer where stage_id = ?", stageId);
        assertThat(timer.get("status")).isEqualTo("RUNNING");
        assertThat(((Number) timer.get("norm_minutes")).intValue()).isEqualTo(1440);

        assertThat(auditActions(flow.requestId()))
                .contains("REQUEST_COMMERCIAL_TERMS_SET", "REQUEST_STATUS_CHANGED");
        assertThat(notificationTypes(flow.requestId()))
                .contains("STAGE_QUEUED", "STATUS_CHANGED");
    }

    @Test
    void techSpecialistClaimsStageAndAvailabilityMovesToContractSigning() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);

        String techToken = login("tech.specialist");
        ApiResponse queue = get("/internal/v1/stages?queueRole=TECH_SPECIALIST&status=QUEUED",
                techToken, Role.TECH_SPECIALIST);
        assertThat(queue.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queueContent = (List<Map<String, Object>>) queue.body().get("content");
        assertThat(queueContent).extracting(item -> item.get("id")).contains(stageId.toString());

        ApiResponse claimed = post("/internal/v1/stages/" + stageId + "/claim",
                techToken, Role.TECH_SPECIALIST, Map.of("version", 0));
        assertThat(claimed.status()).isEqualTo(200);
        assertThat(claimed.body().get("status")).isEqualTo("IN_PROGRESS");
        assertThat(claimed.body().get("assigneeId")).isEqualTo(flow.techId().toString());

        ApiResponse result = post("/internal/v1/stages/" + stageId + "/tech-result",
                techToken, Role.TECH_SPECIALIST,
                Map.of("result", "AVAILABLE", "version", number(claimed.body().get("version"))));
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("status")).isEqualTo("DONE");

        ApiResponse card = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        assertThat(card.status()).isEqualTo(200);
        assertThat(card.body().get("status")).isEqualTo("CONTRACT_SIGNING");
        assertThat(card.body().get("techResult")).isEqualTo("AVAILABLE");
        assertThat(card.body().get("routeVersion")).isEqualTo(2);
        assertThat(card.body().get("commitmentLevel")).isEqualTo(0);

        Map<String, Object> contractStage = firstStage(card);
        assertThat(contractStage.get("type")).isEqualTo("CONTRACT_SIGNING");
        assertThat(contractStage.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(contractStage.get("assigneeId")).isEqualTo(flow.managerId().toString());

        assertThat(auditActions(flow.requestId()))
                .contains("STAGE_CLAIMED", "REQUEST_TECH_RESULT_SET", "REQUEST_STATUS_CHANGED");
        assertThat(notificationTypes(flow.requestId()))
                .contains("STAGE_ASSIGNED", "CONTRACT_READY");
    }

    @Test
    void techResultNoneRejectsRequestWithReason() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        String techToken = login("tech.specialist");
        long stageVersion = claim(stageId, techToken);

        ApiResponse withoutReason = post("/internal/v1/stages/" + stageId + "/tech-result",
                techToken, Role.TECH_SPECIALIST,
                Map.of("result", "NONE", "version", stageVersion));
        assertThat(withoutReason.status()).isEqualTo(422);
        assertThat(withoutReason.body().get("code")).isEqualTo("VALIDATION_FAILED");

        ApiResponse withoutComment = post("/internal/v1/stages/" + stageId + "/tech-result",
                techToken, Role.TECH_SPECIALIST,
                Map.of("result", "NONE", "reasonCode", "OTHER", "version", stageVersion));
        assertThat(withoutComment.status()).isEqualTo(422);

        ApiResponse rejected = post("/internal/v1/stages/" + stageId + "/tech-result",
                techToken, Role.TECH_SPECIALIST,
                Map.of("result", "NONE", "reasonCode", "OTHER",
                        "comment", "Нет технической возможности", "version", stageVersion));
        assertThat(rejected.status()).isEqualTo(200);

        ApiResponse card = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        assertThat(card.body().get("status")).isEqualTo("REJECTED");
        assertThat(card.body().get("techResult")).isEqualTo("NONE");
        assertThat(card.body().get("techRejectReasonCode")).isEqualTo("OTHER");
        assertThat(card.body().get("techRejectReason")).isEqualTo("Нет технической возможности");
        assertThat(notificationTypes(flow.requestId())).contains("TECH_RESULT_NONE");

        assertThat(jdbc.queryForObject(
                "select status from sla_timer where stage_id = ?", String.class, stageId))
                .isEqualTo("STOPPED");
    }

    @Test
    void withConstructionIsRejectedUntilWeekThree() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        String techToken = login("tech.specialist");
        long stageVersion = claim(stageId, techToken);

        ApiResponse result = post("/internal/v1/stages/" + stageId + "/tech-result",
                techToken, Role.TECH_SPECIALIST,
                Map.of("result", "WITH_CONSTRUCTION", "capex", "300000.00",
                        "constructionDays", 10, "version", stageVersion));

        assertThat(result.status()).isEqualTo(409);
        assertThat(result.body().get("code")).isEqualTo("ILLEGAL_TRANSITION");
    }

    @Test
    void stageQueueEnforcesRoleAndCoordinatorCanAssign() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        AppUser lawyer = createUser("lawyer", "Юрист", Role.LAWYER);
        String lawyerToken = login("lawyer");

        ApiResponse wrongRole = post("/internal/v1/stages/" + stageId + "/claim",
                lawyerToken, Role.LAWYER, Map.of("version", 0));
        assertThat(wrongRole.status()).isEqualTo(403);
        assertThat(wrongRole.body().get("code")).isEqualTo("STAGE_NOT_IN_YOUR_QUEUE");

        ApiResponse client = get("/internal/v1/stages", flow.clientToken(), Role.CLIENT_OWNER);
        assertThat(client.status()).isEqualTo(403);
        assertThat(client.body().get("code")).isEqualTo("ACCESS_DENIED");

        String coordinatorToken = login("coordinator");
        ApiResponse assigned = post("/internal/v1/stages/" + stageId + "/assign",
                coordinatorToken, Role.COORDINATOR,
                Map.of("assigneeId", flow.techId().toString(), "version", 0));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().get("status")).isEqualTo("IN_PROGRESS");
        assertThat(assigned.body().get("assigneeId")).isEqualTo(flow.techId().toString());

        ApiResponse secondAssign = post("/internal/v1/stages/" + stageId + "/assign",
                coordinatorToken, Role.COORDINATOR,
                Map.of("assigneeId", lawyer.getId().toString(),
                        "version", number(assigned.body().get("version"))));
        assertThat(secondAssign.status()).isEqualTo(422);
        assertThat(secondAssign.body().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void fullExecutionChainRunsFromContractToClosing() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID techStageId = techCheckStage(flow);
        String techToken = login("tech.specialist");
        long techStageVersion = claim(techStageId, techToken);
        completeTechResult(techStageId, techToken, techStageVersion, "AVAILABLE", null);

        ApiResponse contractSent = post("/internal/v1/requests/" + flow.requestId() + "/contract/sent",
                flow.managerToken(), Role.SALES_MANAGER, Map.of("version", currentRequestVersion(flow)));
        assertThat(contractSent.status()).isEqualTo(200);

        UUID contractStageId = stageIdOfType(flow, "CONTRACT_SIGNING");
        assertThat(jdbc.queryForObject(
                "select status from sla_timer where stage_id = ?", String.class, contractStageId))
                .isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject(
                "select reason_code from sla_pause where timer_id = "
                        + "(select id from sla_timer where stage_id = ?) and ended_at is null",
                String.class, contractStageId)).isEqualTo("AWAIT_CLIENT_SIGNATURE");

        ApiResponse concluded = post("/internal/v1/requests/" + flow.requestId() + "/contract/concluded",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("contractNumber", "Д-2026/17",
                        "concludedAt", businessToday(),
                        "version", number(contractSent.body().get("version"))));
        assertThat(concluded.status()).isEqualTo(200);
        assertThat(concluded.body().get("status")).isEqualTo("IN_EXECUTION");
        assertThat(concluded.body().get("commitmentLevel")).isEqualTo(1);
        assertThat(concluded.body().get("contractNumber")).isEqualTo("Д-2026/17");
        assertThat(stageTypeStatuses(concluded)).containsEntry("CONTRACT_SIGNING", "DONE");

        UUID installationId = stageIdOfType(flow, "INSTALLATION");
        String engineerToken = login("engineer");
        ApiResponse installationClaimed = post("/internal/v1/stages/" + installationId + "/claim",
                engineerToken, Role.ENGINEER, Map.of("version", 0));
        assertThat(installationClaimed.status()).isEqualTo(200);

        ApiResponse afterWorkStarted = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        assertThat(afterWorkStarted.body().get("commitmentLevel")).isEqualTo(2);
        assertThat(afterWorkStarted.body().get("workStartedAt")).isNotNull();

        ApiResponse installationDone = post("/internal/v1/stages/" + installationId + "/complete",
                engineerToken, Role.ENGINEER,
                Map.of("version", number(installationClaimed.body().get("version"))));
        assertThat(installationDone.status()).isEqualTo(200);

        UUID activationId = stageIdOfType(flow, "ACTIVATION");
        ApiResponse activationClaimed = post("/internal/v1/stages/" + activationId + "/claim",
                engineerToken, Role.ENGINEER, Map.of("version", 0));
        assertThat(activationClaimed.status()).isEqualTo(200);

        ApiResponse activated = post("/internal/v1/stages/" + activationId + "/activate",
                engineerToken, Role.ENGINEER,
                Map.of("version", number(activationClaimed.body().get("version"))));
        assertThat(activated.status()).isEqualTo(200);

        ApiResponse afterActivation = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        assertThat(afterActivation.body().get("status")).isEqualTo("ACTIVATED");
        assertThat(afterActivation.body().get("commitmentLevel")).isEqualTo(3);
        assertThat(afterActivation.body().get("activatedAt")).isNotNull();
        assertThat(stageTypeStatuses(afterActivation)).containsEntry("CLOSING", "IN_PROGRESS");

        ApiResponse actSent = post("/internal/v1/requests/" + flow.requestId() + "/act/sent",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", number(afterActivation.body().get("version"))));
        assertThat(actSent.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "select count(*) from sla_timer where request_id = ? and kind = 'ACCEPTANCE' and status = 'RUNNING'",
                Integer.class, flow.requestId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from sla_timer where stage_id = ?", String.class,
                stageIdOfType(flow, "CLOSING"))).isEqualTo("PAUSED");

        ApiResponse closed = post("/internal/v1/requests/" + flow.requestId() + "/act/signed",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", number(actSent.body().get("version"))));
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.body().get("status")).isEqualTo("CLOSED");
        assertThat(closed.body().get("closedAt")).isNotNull();
        assertThat(closed.body().get("acceptedByDefault")).isEqualTo(false);

        assertThat(jdbc.queryForObject(
                "select count(*) from sla_timer where request_id = ? and status <> 'STOPPED'",
                Integer.class, flow.requestId())).isZero();
        assertThat(notificationTypes(flow.requestId()))
                .contains("SERVICE_ACTIVATED", "ACT_SENT", "REQUEST_CLOSED");
        assertThat(auditActions(flow.requestId()))
                .contains("REQUEST_CONTRACT_SENT", "REQUEST_CONTRACT_CONCLUDED",
                        "REQUEST_WORK_STARTED", "STAGE_COMPLETED", "STAGE_ACTIVATED",
                        "REQUEST_ACT_SENT", "REQUEST_ACT_SIGNED");
    }

    @Test
    void clientAcceptsOfferAndMovesRequestToExecution() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID techStageId = techCheckStage(flow);
        String techToken = login("tech.specialist");
        long stageVersion = claim(techStageId, techToken);
        completeTechResult(techStageId, techToken, stageVersion, "AVAILABLE", null);

        long requestVersion = currentRequestVersion(flow);
        ApiResponse accepted = post("/internal/v1/requests/" + flow.requestId() + "/contract/accept-offer",
                flow.clientToken(), Role.CLIENT_OWNER, Map.of("version", requestVersion));

        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body().get("status")).isEqualTo("IN_EXECUTION");
        assertThat(accepted.body().get("commitmentLevel")).isEqualTo(1);
        assertThat(accepted.body().get("contractConcludedAt")).isNotNull();
        assertThat(stageTypeStatuses(accepted)).containsEntry("INSTALLATION", "QUEUED");

        ApiResponse duplicate = post("/internal/v1/requests/" + flow.requestId() + "/contract/accept-offer",
                flow.clientToken(), Role.CLIENT_OWNER,
                Map.of("version", number(accepted.body().get("version"))));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("code")).isEqualTo("ILLEGAL_TRANSITION");
    }

    @Test
    void closeByDefaultWaitsForAcceptanceDeadline() {
        Bootstrap flow = bootstrapWithManager("SMB");
        UUID techStageId = techCheckStage(flow);
        String techToken = login("tech.specialist");
        long techStageVersion = claim(techStageId, techToken);
        completeTechResult(techStageId, techToken, techStageVersion, "AVAILABLE", null);

        ApiResponse contractSent = post("/internal/v1/requests/" + flow.requestId() + "/contract/sent",
                flow.managerToken(), Role.SALES_MANAGER, Map.of("version", currentRequestVersion(flow)));
        post("/internal/v1/requests/" + flow.requestId() + "/contract/concluded",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("contractNumber", "Д-2", "concludedAt", businessToday(),
                        "version", number(contractSent.body().get("version"))));

        String engineerToken = login("engineer");
        UUID installationId = stageIdOfType(flow, "INSTALLATION");
        ApiResponse installationClaimed = post("/internal/v1/stages/" + installationId + "/claim",
                engineerToken, Role.ENGINEER, Map.of("version", 0));
        post("/internal/v1/stages/" + installationId + "/complete",
                engineerToken, Role.ENGINEER,
                Map.of("version", number(installationClaimed.body().get("version"))));

        UUID activationId = stageIdOfType(flow, "ACTIVATION");
        ApiResponse activationClaimed = post("/internal/v1/stages/" + activationId + "/claim",
                engineerToken, Role.ENGINEER, Map.of("version", 0));
        post("/internal/v1/stages/" + activationId + "/activate",
                engineerToken, Role.ENGINEER,
                Map.of("version", number(activationClaimed.body().get("version"))));

        ApiResponse actSent = post("/internal/v1/requests/" + flow.requestId() + "/act/sent",
                flow.managerToken(), Role.SALES_MANAGER, Map.of("version", currentRequestVersion(flow)));

        ApiResponse tooEarly = post("/internal/v1/requests/" + flow.requestId() + "/close-by-default",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", number(actSent.body().get("version"))));
        assertThat(tooEarly.status()).isEqualTo(409);
        assertThat(tooEarly.body().get("code")).isEqualTo("ILLEGAL_TRANSITION");

        jdbc.update("update sla_timer set due_at = now() - interval '1 minute', "
                + "next_event_at = now() - interval '1 minute' "
                + "where request_id = ? and kind = 'ACCEPTANCE'", flow.requestId());

        ApiResponse closed = post("/internal/v1/requests/" + flow.requestId() + "/close-by-default",
                flow.managerToken(), Role.SALES_MANAGER,
                Map.of("version", number(actSent.body().get("version"))));
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.body().get("status")).isEqualTo("CLOSED");
        assertThat(closed.body().get("acceptedByDefault")).isEqualTo(true);
        assertThat(jdbc.queryForObject(
                "select count(*) from sla_timer where request_id = ? and status <> 'STOPPED'",
                Integer.class, flow.requestId())).isZero();
    }

    @Test
    void commercialTermsRequireDiscountForIndividualTariffAndValidateRange() {
        Bootstrap flow = bootstrapWithManager("SMB");

        ApiResponse missingDiscount = setCommercialTerms(flow, Map.of(
                "tariffType", "INDIVIDUAL", "contractType", "OFFER",
                "version", flow.requestVersion()));
        assertThat(missingDiscount.status()).isEqualTo(422);
        assertThat(missingDiscount.body().get("code")).isEqualTo("VALIDATION_FAILED");

        ApiResponse invalidDiscount = setCommercialTerms(flow, Map.of(
                "tariffType", "INDIVIDUAL", "contractType", "OFFER",
                "discountPercent", "150.00", "version", flow.requestVersion()));
        assertThat(invalidDiscount.status()).isEqualTo(422);

        ApiResponse valid = setCommercialTerms(flow, Map.of(
                "tariffType", "INDIVIDUAL", "contractType", "OFFER",
                "discountPercent", "15.00", "version", flow.requestVersion()));
        assertThat(valid.status()).isEqualTo(200);
        assertThat(valid.body().get("requestClass")).isEqualTo("EXTENDED");

        ApiResponse duplicate = setCommercialTerms(flow, Map.of(
                "tariffType", "STANDARD", "contractType", "OFFER",
                "version", number(valid.body().get("version"))));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("code")).isEqualTo("ILLEGAL_TRANSITION");
    }

    // --- сценарные помощники ---

    private Bootstrap bootstrapWithManager(String segment) {
        AppUser client = createUser("client.owner", "Алексей Соколов");
        Company company = createCompany("ИП Соколов", INN, CompanyStatus.ACTIVE, client);
        AppUser manager = createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        AppUser tech = createUser("tech.specialist", "Павел Орлов", Role.TECH_SPECIALIST);
        createUser("engineer", "Инженер", Role.ENGINEER);
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);
        stubAllExternalSystems(INN, segment, "sales.manager");

        String clientToken = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", clientToken, Role.CLIENT_OWNER, requestPayload());
        assertThat(created.status()).isEqualTo(201);
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                clientToken, Role.CLIENT_OWNER, Map.of("version", number(created.body().get("version"))));
        assertThat(submitted.status()).isEqualTo(200);
        assertThat(submitted.body().get("status")).isEqualTo("WITH_MANAGER");
        // Клиентская карточка скрывает managerId, поэтому проверяем назначение в БД.
        assertThat(jdbc.queryForObject("select manager_id from request where id = ?",
                UUID.class, requestId)).isEqualTo(manager.getId());

        return new Bootstrap(requestId, number(submitted.body().get("version")),
                client.getId(), manager.getId(), tech.getId(),
                clientToken, login("sales.manager"), login("tech.specialist"), company.getId());
    }

    private ApiResponse setCommercialTerms(Bootstrap flow, Map<String, Object> body) {
        return post("/internal/v1/requests/" + flow.requestId() + "/commercial-terms",
                flow.managerToken(), Role.SALES_MANAGER, body);
    }

    private UUID techCheckStage(Bootstrap flow) {
        ApiResponse terms = setCommercialTerms(flow, Map.of(
                "tariffType", "STANDARD", "contractType", "OFFER",
                "version", flow.requestVersion()));
        assertThat(terms.status()).isEqualTo(200);
        return UUID.fromString((String) firstStage(terms).get("id"));
    }

    private long claim(UUID stageId, String token) {
        ApiResponse claimed = post("/internal/v1/stages/" + stageId + "/claim",
                token, Role.TECH_SPECIALIST, Map.of("version", 0));
        assertThat(claimed.status()).isEqualTo(200);
        return number(claimed.body().get("version"));
    }

    private void completeTechResult(UUID stageId, String token, long stageVersion,
                                    String result, String reasonCode) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("result", result);
        body.put("version", stageVersion);
        if (reasonCode != null) {
            body.put("reasonCode", reasonCode);
            body.put("comment", "Причина");
        }
        ApiResponse response = post("/internal/v1/stages/" + stageId + "/tech-result",
                token, Role.TECH_SPECIALIST, body);
        assertThat(response.status()).isEqualTo(200);
    }

    private UUID stageIdOfType(Bootstrap flow, String type) {
        return jdbc.queryForObject(
                "select id from request_stage where request_id = ? and type = ? "
                        + "and status in ('QUEUED', 'IN_PROGRESS') order by created_at desc limit 1",
                UUID.class, flow.requestId(), type);
    }

    private long currentRequestVersion(Bootstrap flow) {
        ApiResponse card = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        return number(card.body().get("version"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstStage(ApiResponse response) {
        return ((List<Map<String, Object>>) response.body().get("stages")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stageTypeStatuses(ApiResponse response) {
        return ((List<Map<String, Object>>) response.body().get("stages")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        stage -> (String) stage.get("type"),
                        stage -> (String) stage.get("status")));
    }

    private List<String> auditActions(UUID requestId) {
        return jdbc.queryForList(
                "select action from audit_log where request_id = ? order by created_at, id",
                String.class, requestId);
    }

    private List<String> notificationTypes(UUID requestId) {
        return jdbc.queryForList(
                "select event_type from notification where request_id = ? order by created_at",
                String.class, requestId);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    /** «Сегодня» для даты договора — по часовому поясу календаря оператора (api-contract.md, 10.11). */
    private static String businessToday() {
        return LocalDate.now(ZoneId.of("Europe/Moscow")).toString();
    }

    private record Bootstrap(UUID requestId, long requestVersion, UUID clientId, UUID managerId,
                             UUID techId, String clientToken, String managerToken, String techToken,
                             UUID companyId) {
    }
}
