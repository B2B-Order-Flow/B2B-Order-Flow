package com.b2borderflow.core.support;

import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.user.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Общий сценарий недели 2 для тестов, которым нужна заявка с менеджером,
 * ТС и инженером после T01–T03b.
 */
public abstract class WeekTwoApiSupport extends AbstractIntegrationTest {

    protected static final String INN = "7725012341";

    protected Flow bootstrapWithManager(String segment) {
        AppUser client = createUser("client.owner", "Алексей Соколов");
        createCompany("ИП Соколов", INN, CompanyStatus.ACTIVE, client);
        AppUser manager = createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        AppUser tech = createUser("tech.specialist", "Павел Орлов", Role.TECH_SPECIALIST);
        AppUser engineer = createUser("engineer", "Инженер", Role.ENGINEER);
        AppUser coordinator = createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);
        AppUser b2bHead = createUser("b2b.head", "Руководитель B2B", Role.B2B_HEAD);
        stubAllExternalSystems(INN, segment, "sales.manager");

        String clientToken = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", clientToken, Role.CLIENT_OWNER, requestPayload());
        assertStatus(created, 201);
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                clientToken, Role.CLIENT_OWNER, Map.of("version", number(created.body().get("version"))));
        assertStatus(submitted, 200);
        if (!"WITH_MANAGER".equals(submitted.body().get("status"))) {
            throw new AssertionError("Ожидали WITH_MANAGER: " + submitted.body());
        }

        return new Flow(requestId, manager.getId(), tech.getId(), engineer.getId(), coordinator.getId(),
                b2bHead.getId(), clientToken, login("sales.manager"), login("tech.specialist"),
                login("engineer"), login("coordinator"), login("b2b.head"),
                number(submitted.body().get("version")));
    }

    protected ApiResponse setCommercialTerms(Flow flow, Map<String, Object> body) {
        return post("/internal/v1/requests/" + flow.requestId() + "/commercial-terms",
                flow.managerToken(), Role.SALES_MANAGER, body);
    }

    protected UUID techCheckStage(Flow flow) {
        ApiResponse terms = setCommercialTerms(flow, Map.of(
                "tariffType", "STANDARD", "contractType", "OFFER",
                "version", flow.requestVersion()));
        assertStatus(terms, 200);
        return UUID.fromString((String) firstStage(terms).get("id"));
    }

    protected long claimStage(UUID stageId, String token) {
        ApiResponse claimed = post("/internal/v1/stages/" + stageId + "/claim",
                token, Role.TECH_SPECIALIST, Map.of("version", 0));
        assertStatus(claimed, 200);
        return number(claimed.body().get("version"));
    }

    protected long currentRequestVersion(Flow flow) {
        ApiResponse card = get("/internal/v1/requests/" + flow.requestId(),
                flow.managerToken(), Role.SALES_MANAGER);
        return number(card.body().get("version"));
    }

    protected UUID activeStageId(Flow flow, String type) {
        return jdbc.queryForObject(
                "select id from request_stage where request_id = ? and type = ? "
                        + "and status in ('QUEUED', 'IN_PROGRESS') order by created_at desc limit 1",
                UUID.class, flow.requestId(), type);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> firstStage(ApiResponse response) {
        return ((List<Map<String, Object>>) response.body().get("stages")).get(0);
    }

    protected static void assertStatus(ApiResponse response, int expected) {
        if (response.status() != expected) {
            throw new AssertionError("Ожидали HTTP " + expected + ", получили " + response.status()
                    + ": " + response.body());
        }
    }

    protected static long number(Object value) {
        return ((Number) value).longValue();
    }

    protected record Flow(UUID requestId, UUID managerId, UUID techId, UUID engineerId,
                          UUID coordinatorId, UUID b2bHeadId,
                          String clientToken, String managerToken, String techToken,
                          String engineerToken, String coordinatorToken, String b2bHeadToken,
                          long requestVersion) {
    }
}
