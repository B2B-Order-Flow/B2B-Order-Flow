package com.b2borderflow.core.request;

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
import org.springframework.http.HttpMethod;

/** EC-16: данные одной компании недоступны другой, попытка попадает в аудит. */
class RequestAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void clientCannotOpenRequestOfAnotherCompany() {
        AppUser ownerA = createUser("owner.a", "Владелец А");
        createCompany("ООО «Логистик Плюс»", "7725012341", CompanyStatus.ACTIVE, ownerA);
        AppUser ownerB = createUser("owner.b", "Владелец Б");
        createCompany("ООО «Вектор»", "7811001128", CompanyStatus.ACTIVE, ownerB);

        String tokenA = login("owner.a");
        ApiResponse created = post("/internal/v1/requests", tokenA, Role.CLIENT_OWNER, requestPayload());
        assertThat(created.status()).isEqualTo(201);
        UUID requestId = UUID.fromString((String) created.body().get("id"));

        String tokenB = login("owner.b");
        ApiResponse foreign = get("/internal/v1/requests/" + requestId, tokenB, Role.CLIENT_OWNER);

        assertThat(foreign.status()).isEqualTo(404);
        assertThat(foreign.body().get("code")).isEqualTo("NOT_FOUND");
        assertThat(foreign.body().get("correlationId")).isNotNull();

        ApiResponse list = get("/internal/v1/requests", tokenB, Role.CLIENT_OWNER);
        assertThat(list.status()).isEqualTo(200);
        assertThat((List<?>) list.body().get("content")).isEmpty();

        // Фильтр по чужой компании не должен расширять выборку.
        ApiResponse filtered = get("/internal/v1/requests?companyId="
                + created.body().get("companyId"), tokenB, Role.CLIENT_OWNER);
        assertThat(filtered.status()).isEqualTo(200);
        assertThat((List<?>) filtered.body().get("content")).isEmpty();

        List<Map<String, Object>> denied = jdbc.queryForList(
                "select action, actor_id, company_id from audit_log "
                        + "where request_id = ? and action = 'ACCESS_DENIED'",
                requestId);
        assertThat(denied).hasSize(1);
        assertThat(denied.get(0).get("actor_id")).isEqualTo(ownerB.getId());
    }

    @Test
    void requestWithoutBearerTokenIsRejectedAsProblem() {
        AppUser owner = createUser("owner.a", "Владелец А");
        createCompany("ООО «Логистик Плюс»", "7725012341", CompanyStatus.ACTIVE, owner);
        String token = login("owner.a");
        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());
        UUID requestId = UUID.fromString((String) created.body().get("id"));

        ApiResponse response = request(HttpMethod.GET, "/internal/v1/requests/" + requestId,
                internalHeaders(), null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.body().get("correlationId")).isNotNull();
        assertThat(response.body()).doesNotContainKey("stackTrace");
    }

    @Test
    void deletedDraftIsNotVisible() {
        AppUser owner = createUser("owner.a", "Владелец А");
        createCompany("ООО «Логистик Плюс»", "7725012341", CompanyStatus.ACTIVE, owner);
        String token = login("owner.a");

        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());
        UUID requestId = UUID.fromString((String) created.body().get("id"));
        long version = ((Number) created.body().get("version")).longValue();

        ApiResponse deleted = request(HttpMethod.DELETE,
                "/internal/v1/requests/" + requestId + "?version=" + version,
                authorizedHeaders(token, Role.CLIENT_OWNER), null);
        assertThat(deleted.status()).isEqualTo(204);

        assertThat(get("/internal/v1/requests/" + requestId, token, Role.CLIENT_OWNER).status())
                .isEqualTo(404);
    }
}
