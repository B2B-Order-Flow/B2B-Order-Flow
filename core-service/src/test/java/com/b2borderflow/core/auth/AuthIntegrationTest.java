package com.b2borderflow.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Вход, регистрация и защита внутреннего API. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void userCanRegisterLoginAndReadProfile() {
        ApiResponse registered = request(HttpMethod.POST, "/internal/v1/auth/register", internalHeaders(),
                Map.of(
                        "login", "client.owner",
                        "password", "password123",
                        "fullName", "Клиент Владелец",
                        "email", "client@example.test"));
        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.body().get("login")).isEqualTo("client.owner");

        String token = login("client.owner");
        assertThat(token).isNotBlank();

        ApiResponse me = get("/internal/v1/me", token, null);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().get("login")).isEqualTo("client.owner");
        assertThat(me.body().get("operatorRoles")).isEqualTo(java.util.List.of());
    }

    @Test
    void duplicateLoginIsRejected() {
        Map<String, Object> body = Map.of(
                "login", "duplicate",
                "password", "password123",
                "fullName", "Дубль",
                "email", "duplicate@example.test");
        assertThat(request(HttpMethod.POST, "/internal/v1/auth/register", internalHeaders(), body).status())
                .isEqualTo(201);
        ApiResponse second = request(HttpMethod.POST, "/internal/v1/auth/register", internalHeaders(), body);
        assertThat(second.status()).isEqualTo(422);
        assertThat(second.body().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void invalidPasswordGivesUnauthorizedProblem() {
        createUser("client.owner", "Клиент Владелец");
        ApiResponse response = request(HttpMethod.POST, "/internal/v1/auth/login", internalHeaders(),
                Map.of("login", "client.owner", "password", "wrong-password"));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().get("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.body().get("correlationId")).isNotNull();
    }

    @Test
    void requestWithoutInternalTokenIsRejected() {
        ApiResponse response = request(HttpMethod.POST, "/internal/v1/auth/login",
                new org.springframework.http.HttpHeaders(),
                Map.of("login", "nobody", "password", "password123"));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void multipleRolesRequireActiveRoleAndForeignRoleIsForbidden() {
        createUser("both.roles", "Игорь Ким", com.b2borderflow.core.domain.Role.TECH_SPECIALIST,
                com.b2borderflow.core.domain.Role.ENGINEER);
        String token = login("both.roles");

        ApiResponse withoutRole = get("/internal/v1/requests", token, null);
        assertThat(withoutRole.status()).isEqualTo(422);
        assertThat(withoutRole.body().get("code")).isEqualTo("ACTIVE_ROLE_REQUIRED");

        ApiResponse foreignRole = get("/internal/v1/requests", token,
                com.b2borderflow.core.domain.Role.ADMIN);
        assertThat(foreignRole.status()).isEqualTo(403);
        assertThat(foreignRole.body().get("code")).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void unsupportedMethodIsProblemWithMethodNotAllowedCode() {
        ApiResponse response = request(HttpMethod.GET, "/internal/v1/auth/login", internalHeaders(), null);
        assertThat(response.status()).isEqualTo(405);
        assertThat(response.body().get("code")).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.body().get("correlationId")).isNotNull();
    }
}
