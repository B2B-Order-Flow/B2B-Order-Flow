package com.b2borderflow.core.request;

import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import com.b2borderflow.core.user.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Вертикальный срез недели 1: черновик → отправка → T01–T03b. */
class RequestSubmissionIntegrationTest extends AbstractIntegrationTest {

    private static final String INN = "7725012341";

    @Test
    void clientSubmitsRequestAndCrmManagerGetsIt() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        Company company = createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        AppUser manager = createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        stubAllExternalSystems(INN, "LARGE", "sales.manager");

        String token = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("status")).isEqualTo("DRAFT");
        assertThat(created.body().get("companyId")).isEqualTo(company.getId().toString());
        assertThat(created.body().get("number")).asString().matches("REQ-\\d{4}-\\d{6}");
        assertThat(created.body().get("selectedOfferId")).isNotNull();
        assertThat(offers(created)).hasSize(1);
        assertThat(offers(created).get(0).get("status")).isEqualTo("SELECTED");

        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = number(created.body().get("version"));

        ApiResponse submitted = request(HttpMethod.POST,
                "/internal/v1/requests/" + requestId + "/submit",
                correlationHeaders(token, Role.CLIENT_OWNER, "test-correlation-1"),
                Map.of("version", version));

        assertThat(submitted.status()).isEqualTo(200);
        assertThat(submitted.body().get("status")).isEqualTo("WITH_MANAGER");
        assertThat(submitted.body().get("integrationError")).isEqualTo(false);
        assertThat(warnings(submitted)).isEmpty();
        assertThat(jdbc.queryForList(
                "select correlation_id from audit_log where request_id = ?", String.class, requestId))
                .contains("test-correlation-1");
        EXTERNAL_MOCKS.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/crm/v1/customers"))
                .withHeader("X-Correlation-Id",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo("test-correlation-1")));

        Map<String, Object> stored = jdbc.queryForMap(
                "select status, manager_id, integration_error, segment, debt_amount from request where id = ?",
                requestId);
        assertThat(stored.get("status")).isEqualTo("WITH_MANAGER");
        assertThat(stored.get("manager_id")).isEqualTo(manager.getId());
        assertThat(stored.get("integration_error")).isEqualTo(false);
        assertThat(stored.get("segment")).isEqualTo("LARGE");
        assertThat(stored.get("debt_amount").toString()).isEqualTo("0.00");

        assertThat(jdbc.queryForObject(
                "select count(*) from external_check where request_id = ? and status = 'OK'",
                Integer.class, requestId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from request_revision where request_id = ? and revision_no = 1 and reason = 'SUBMIT'",
                Integer.class, requestId)).isEqualTo(1);

        List<String> actions = auditRows(requestId).stream()
                .map(row -> (String) row.get("action"))
                .toList();
        assertThat(actions).containsExactly(
                "REQUEST_CREATED",
                "REQUEST_SUBMITTED",
                "REQUEST_DATA_CHECK_COMPLETED",
                "REQUEST_MANAGER_ASSIGNED");

        // Клиентская карточка не содержит исполнителя и внутренних сумм.
        ApiResponse clientView = get("/internal/v1/requests/" + requestId, token, Role.CLIENT_OWNER);
        assertThat(clientView.status()).isEqualTo(200);
        assertThat(clientView.body()).doesNotContainKeys("managerId", "debtAmount", "crmManagerId");

        // Менеджер видит назначенную ему заявку.
        String managerToken = login("sales.manager");
        ApiResponse managerView = get("/internal/v1/requests/" + requestId, managerToken, Role.SALES_MANAGER);
        assertThat(managerView.status()).isEqualTo(200);
        assertThat(managerView.body().get("managerId")).isEqualTo(manager.getId().toString());
        assertThat(managerView.body().get("debtAmount")).isEqualTo("0.00");
    }

    @Test
    void retriesMakeFlakyCrmSuccessful() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        stubCrmFlaky(INN, "sales.manager");
        stubBilling(INN, "0.00");
        stubRegistry(INN, false);

        String token = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = number(created.body().get("version"));

        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                token, Role.CLIENT_OWNER, Map.of("version", version));

        assertThat(submitted.status()).isEqualTo(200);
        assertThat(submitted.body().get("status")).isEqualTo("WITH_MANAGER");
        Map<String, Object> crmCheck = jdbc.queryForMap(
                "select status, attempts from external_check where request_id = ? and system = 'CRM'",
                requestId);
        assertThat(crmCheck.get("status")).isEqualTo("OK");
        assertThat(((Number) crmCheck.get("attempts")).intValue()).isEqualTo(3);
    }

    @Test
    void integrationFailureKeepsRequestInDataCheckAndRecheckFinishesIt() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);

        stubCrmUnavailable(INN);
        stubBilling(INN, "0.00");
        stubRegistry(INN, false);

        String token = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = number(created.body().get("version"));

        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                token, Role.CLIENT_OWNER, Map.of("version", version));

        assertThat(submitted.status()).isEqualTo(200);
        assertThat(submitted.body().get("status")).isEqualTo("DATA_CHECK");
        assertThat(submitted.body().get("integrationError")).isEqualTo(true);
        assertThat(warnings(submitted)).extracting(warning -> warning.get("code"))
                .containsExactly("EXTERNAL_SYSTEM_UNAVAILABLE");

        Map<String, Object> crmCheck = jdbc.queryForMap(
                "select status, attempts from external_check where request_id = ? and system = 'CRM'",
                requestId);
        assertThat(crmCheck.get("status")).isEqualTo("ERROR");
        assertThat(((Number) crmCheck.get("attempts")).intValue()).isEqualTo(3);

        assertThat(auditRows(requestId).stream().map(row -> row.get("action")))
                .containsSubsequence("REQUEST_SUBMITTED", "REQUEST_DATA_CHECK_FAILED")
                .doesNotContain("REQUEST_MANAGER_ASSIGNED");

        // Координатор повторяет проверку после восстановления CRM.
        EXTERNAL_MOCKS.resetAll();
        stubAllExternalSystems(INN, "LARGE", "sales.manager");

        String coordinatorToken = login("coordinator");
        ApiResponse current = get("/internal/v1/requests/" + requestId, coordinatorToken, Role.COORDINATOR);
        long currentVersion = number(current.body().get("version"));

        ApiResponse rechecked = post("/internal/v1/requests/" + requestId + "/recheck-data",
                coordinatorToken, Role.COORDINATOR, Map.of("version", currentVersion));

        assertThat(rechecked.status()).isEqualTo(200);
        assertThat(rechecked.body().get("status")).isEqualTo("WITH_MANAGER");
        assertThat(rechecked.body().get("integrationError")).isEqualTo(false);
        assertThat(jdbc.queryForObject(
                "select count(*) from external_check where request_id = ? and system = 'CRM' and status = 'OK'",
                Integer.class, requestId)).isEqualTo(1);
    }

    @Test
    void managerCanClaimRequestFromQueueOrCoordinatorCanAssignIt() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        AppUser firstManager = createUser("first.manager", "Первый Менеджер", Role.SALES_MANAGER);
        AppUser secondManager = createUser("second.manager", "Второй Менеджер", Role.SALES_MANAGER);
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);

        stubAllExternalSystems(INN, "LARGE", null);

        String clientToken = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", clientToken, Role.CLIENT_OWNER, requestPayload());
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = number(created.body().get("version"));
        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                clientToken, Role.CLIENT_OWNER, Map.of("version", version));
        assertThat(submitted.body().get("status")).isEqualTo("DATA_CHECK");
        assertThat(submitted.body().get("managerId")).isNull();
        long afterSubmitVersion = number(submitted.body().get("version"));

        String managerToken = login("first.manager");
        ApiResponse queue = get("/internal/v1/requests?status=DATA_CHECK&unassigned=true",
                managerToken, Role.SALES_MANAGER);
        assertThat(queue.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queueContent = (List<Map<String, Object>>) queue.body().get("content");
        assertThat(queueContent).extracting(item -> item.get("id")).contains(requestId.toString());

        ApiResponse claimed = post("/internal/v1/requests/" + requestId + "/claim",
                managerToken, Role.SALES_MANAGER, Map.of("version", afterSubmitVersion));

        assertThat(claimed.status()).isEqualTo(200);
        assertThat(claimed.body().get("status")).isEqualTo("WITH_MANAGER");
        assertThat(claimed.body().get("managerId")).isEqualTo(firstManager.getId().toString());

        // Второй менеджер уже не может взять заявку.
        ApiResponse secondClaim = post("/internal/v1/requests/" + requestId + "/claim",
                login("second.manager"), Role.SALES_MANAGER, Map.of("version", number(claimed.body().get("version"))));
        assertThat(secondClaim.status()).isEqualTo(409);
        assertThat(secondClaim.body().get("code")).isEqualTo("ILLEGAL_TRANSITION");

        // Координатор переназначает менеджера в нетерминальном статусе.
        String coordinatorToken = login("coordinator");
        ApiResponse current = get("/internal/v1/requests/" + requestId, coordinatorToken, Role.COORDINATOR);
        ApiResponse reassigned = post("/internal/v1/requests/" + requestId + "/assign-manager",
                coordinatorToken, Role.COORDINATOR,
                Map.of("managerId", secondManager.getId().toString(),
                        "version", number(current.body().get("version"))));

        assertThat(reassigned.status()).isEqualTo(200);
        assertThat(reassigned.body().get("status")).isEqualTo("WITH_MANAGER");
        assertThat(reassigned.body().get("managerId")).isEqualTo(secondManager.getId().toString());
        assertThat(auditRows(requestId).stream().map(row -> row.get("action")))
                .containsSubsequence("REQUEST_MANAGER_CLAIMED", "REQUEST_MANAGER_REASSIGNED");
    }

    @Test
    void coordinatorAssignsManagerWhenCrmDoesNotKnowClient() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Агрохолдинг Заря»", "5050123455", CompanyStatus.ACTIVE, client);
        AppUser manager = createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);
        stubAllExternalSystems("5050123455", "STRATEGIC", null);

        String clientToken = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", clientToken, Role.CLIENT_OWNER,
                Map.of("address", "Москва, ул. Тверская, 1",
                        "requestedSpeedMbps", 200,
                        "desiredDate", "2026-12-05",
                        "contactName", "Татьяна Морозова",
                        "contactPhone", "+7 999 111-22-33"));
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = number(created.body().get("version"));
        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                clientToken, Role.CLIENT_OWNER, Map.of("version", version));
        assertThat(submitted.body().get("status")).isEqualTo("DATA_CHECK");
        long afterSubmitVersion = number(submitted.body().get("version"));

        String coordinatorToken = login("coordinator");
        ApiResponse assigned = post("/internal/v1/requests/" + requestId + "/assign-manager",
                coordinatorToken, Role.COORDINATOR,
                Map.of("managerId", manager.getId().toString(), "version", afterSubmitVersion));

        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().get("status")).isEqualTo("WITH_MANAGER");
        assertThat(assigned.body().get("managerId")).isEqualTo(manager.getId().toString());
        assertThat(auditRows(requestId).stream().map(row -> row.get("action")))
                .contains("REQUEST_MANAGER_ASSIGNED");
    }

    @Test
    void idempotencyKeyReturnsSameRequestAndRejectsDifferentBody() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        String token = login("client.owner");
        UUID key = UUID.randomUUID();

        HttpHeaders headers = authorizedHeaders(token, Role.CLIENT_OWNER);
        headers.set("Idempotency-Key", key.toString());

        ApiResponse first = request(HttpMethod.POST, "/internal/v1/requests", headers, requestPayload());
        ApiResponse second = request(HttpMethod.POST, "/internal/v1/requests", headers, requestPayload());

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.body().get("id")).isEqualTo(first.body().get("id"));
        assertThat(jdbc.queryForObject("select count(*) from request", Integer.class)).isEqualTo(1);

        ApiResponse conflict = request(HttpMethod.POST, "/internal/v1/requests", headers,
                Map.of("address", "Другой адрес"));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().get("code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void submitValidatesRequiredFields() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", INN, CompanyStatus.ACTIVE, client);
        String token = login("client.owner");

        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, Map.of());
        assertThat(created.status()).isEqualTo(201);
        UUID requestId = UUID.fromString((String) created.body().get("id"));

        ApiResponse submitted = post("/internal/v1/requests/" + requestId + "/submit",
                token, Role.CLIENT_OWNER, Map.of("version", number(created.body().get("version"))));

        assertThat(submitted.status()).isEqualTo(422);
        assertThat(submitted.body().get("code")).isEqualTo("VALIDATION_FAILED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violations = (List<Map<String, Object>>) submitted.body().get("violations");
        assertThat(violations).extracting(item -> item.get("field"))
                .contains("address", "contactName", "contactPhone");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> offers(ApiResponse response) {
        return (List<Map<String, Object>>) response.body().get("offers");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> warnings(ApiResponse response) {
        return (List<Map<String, Object>>) response.body().getOrDefault("warnings", List.of());
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private org.springframework.http.HttpHeaders correlationHeaders(String token, Role role, String correlationId) {
        org.springframework.http.HttpHeaders headers = authorizedHeaders(token, role);
        headers.set("X-Correlation-Id", correlationId);
        return headers;
    }
}
