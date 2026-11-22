package com.b2borderflow.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** GET /users — выбор сотрудника оператора (api-contract.md, 6.1). */
class UsersIntegrationTest extends AbstractIntegrationTest {

    @Test
    void coordinatorSeesOnlyActiveUsersWithRequestedRole() {
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);
        AppUser inactive = createUser("old.manager", "Пётр Старый", Role.SALES_MANAGER);
        inactive.setActive(false);
        userRepository.saveAndFlush(inactive);
        createUser("tech", "Павел Орлов", Role.TECH_SPECIALIST);

        String coordinatorToken = login("coordinator");
        ApiResponse response = get("/internal/v1/users?role=SALES_MANAGER",
                coordinatorToken, Role.COORDINATOR);

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.body().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("login")).isEqualTo("sales.manager");
        assertThat(content.get(0).get("fullName")).isEqualTo("Анна Смирнова");
        assertThat(content.get(0).get("active")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> operatorRoles = (List<String>) content.get(0).get("operatorRoles");
        assertThat(operatorRoles).containsExactly("SALES_MANAGER");
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) response.body().get("page");
        assertThat(page.get("totalElements")).isEqualTo(1);
    }

    @Test
    void nonCoordinatorCannotReadOperatorUsers() {
        createUser("sales.manager", "Анна Смирнова", Role.SALES_MANAGER);

        ApiResponse response = get("/internal/v1/users?role=SALES_MANAGER",
                login("sales.manager"), Role.SALES_MANAGER);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().get("code")).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void invalidSortIsValidationError() {
        createUser("coordinator", "Мария Ковалёва", Role.COORDINATOR);

        ApiResponse response = get("/internal/v1/users?role=SALES_MANAGER&sort=password,asc",
                login("coordinator"), Role.COORDINATOR);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().get("code")).isEqualTo("VALIDATION_FAILED");
    }
}
