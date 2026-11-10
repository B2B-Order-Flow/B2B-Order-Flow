package com.b2borderflow.core.company;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import com.b2borderflow.core.user.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-00: регистрация компании, проверка ИНН и подтверждение администратором. */
class CompanyIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_INN = "770112345127";

    @Test
    void guestRegistersCompanyAndAdminConfirmsIt() {
        AppUser guest = createUser("guest", "Глеб Тихонов");
        stubRegistry(VALID_INN, false);
        String guestToken = login("guest");

        ApiResponse registered = post("/internal/v1/companies", guestToken, null, Map.of(
                "name", "ИП Соколов А. В.",
                "inn", VALID_INN,
                "legalAddress", "г. Екатеринбург, ул. Мира, 10"));

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.body().get("status")).isEqualTo("PENDING");
        UUID companyId = UUID.fromString((String) registered.body().get("id"));

        assertThat(jdbc.queryForObject(
                "select count(*) from membership_role mr join company_membership cm on cm.id = mr.membership_id "
                        + "where cm.company_id = ? and cm.user_id = ? and mr.role = 'CLIENT_OWNER'",
                Integer.class, companyId, guest.getId())).isEqualTo(1);

        AppUser admin = createUser("admin", "Администратор", Role.ADMIN);
        String adminToken = login("admin");

        ApiResponse pending = get("/internal/v1/admin/companies?status=PENDING", adminToken, Role.ADMIN);
        assertThat(pending.status()).isEqualTo(200);
        assertThat((List<?>) pending.body().get("content")).hasSize(1);

        ApiResponse confirmed = post("/internal/v1/admin/companies/" + companyId + "/confirm",
                adminToken, Role.ADMIN, Map.of("comment", "Реквизиты сверены"));
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.body().get("status")).isEqualTo("ACTIVE");
        assertThat(confirmed.body().get("confirmedAt")).isNotNull();

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where entity_id = ? and action = 'COMPANY_CONFIRMED'",
                Integer.class, companyId)).isEqualTo(1);
    }

    @Test
    void invalidInnIsRejectedWithoutRegistryCall() {
        createUser("guest", "Глеб Тихонов");
        String token = login("guest");

        ApiResponse response = post("/internal/v1/companies", token, null, Map.of(
                "name", "Плохая компания",
                "inn", "1234567890",
                "legalAddress", "Адрес"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().get("code")).isEqualTo("VALIDATION_FAILED");
        EXTERNAL_MOCKS.verify(0, getRequestedFor(urlPathEqualTo("/registry/v1/organizations")));
    }

    @Test
    void duplicateActiveInnIsRejectedWithoutRegistryCall() {
        AppUser owner = createUser("owner", "Владелец");
        createCompany("ООО «Логистик Плюс»", "7725012341", CompanyStatus.ACTIVE, owner);
        createUser("guest", "Глеб Тихонов");
        String token = login("guest");

        ApiResponse response = post("/internal/v1/companies", token, null, Map.of(
                "name", "Дубль",
                "inn", "7725012341",
                "legalAddress", "Адрес"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("code")).isEqualTo("COMPANY_INN_EXISTS");
        EXTERNAL_MOCKS.verify(0, getRequestedFor(urlPathEqualTo("/registry/v1/organizations")));
    }

    @Test
    void adminRejectsCompanyOnlyWithReason() {
        AppUser owner = createUser("owner", "Владелец");
        Company company = createCompany("ООО «Вектор»", "7811001128", CompanyStatus.PENDING, owner);
        createUser("admin", "Администратор", Role.ADMIN);
        String adminToken = login("admin");

        ApiResponse withoutReason = post("/internal/v1/admin/companies/" + company.getId() + "/reject",
                adminToken, Role.ADMIN, Map.of());
        assertThat(withoutReason.status()).isEqualTo(422);
        assertThat(withoutReason.body().get("code")).isEqualTo("VALIDATION_FAILED");

        ApiResponse rejected = post("/internal/v1/admin/companies/" + company.getId() + "/reject",
                adminToken, Role.ADMIN, Map.of("reason", "Документы не подтверждены"));
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().get("status")).isEqualTo("REJECTED");
        assertThat(rejected.body().get("rejectReason")).isEqualTo("Документы не подтверждены");
    }
}
