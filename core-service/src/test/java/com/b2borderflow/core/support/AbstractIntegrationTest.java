package com.b2borderflow.core.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyMembership;
import com.b2borderflow.core.company.CompanyMembershipRepository;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.company.MembershipRole;
import com.b2borderflow.core.company.MembershipRoleId;
import com.b2borderflow.core.company.MembershipRoleRepository;
import com.b2borderflow.core.company.OperatorRole;
import com.b2borderflow.core.company.OperatorRoleId;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Базовый интеграционный тест: PostgreSQL 16 в Testcontainers 2.x и WireMock
 * для трёх внешних систем. Каждый тест видит чистую БД.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "password123";

    protected static final PostgreSQLContainer POSTGRES;
    protected static final WireMockServer EXTERNAL_MOCKS;

    static {
        POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("core_db")
                .withUsername("core_app")
                .withPassword("core_app");
        POSTGRES.start();

        EXTERNAL_MOCKS = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        EXTERNAL_MOCKS.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EXTERNAL_MOCKS.stop();
            POSTGRES.stop();
        }, "test-infrastructure-shutdown"));
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.app-user", POSTGRES::getUsername);
        registry.add("app.integration.crm-url", () -> "http://localhost:" + EXTERNAL_MOCKS.port());
        registry.add("app.integration.billing-url", () -> "http://localhost:" + EXTERNAL_MOCKS.port());
        registry.add("app.integration.registry-url", () -> "http://localhost:" + EXTERNAL_MOCKS.port());
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected AppUserRepository userRepository;

    @Autowired
    protected CompanyRepository companyRepository;

    @Autowired
    protected CompanyMembershipRepository membershipRepository;

    @Autowired
    protected MembershipRoleRepository membershipRoleRepository;

    @Autowired
    protected OperatorRoleRepository operatorRoleRepository;

    @Value("${app.security.internal-token}")
    protected String internalToken;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                truncate table audit_log, external_check, request_revision, request_stage,
                    service_offer, idempotency_key, request, membership_role, company_membership,
                    operator_role, company, app_user cascade
                """);
        EXTERNAL_MOCKS.resetAll();
    }

    // --- данные ---

    protected AppUser createUser(String login, String fullName, Role... operatorRoles) {
        AppUser user = new AppUser();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setEmail(login + "@example.test");
        user.setActive(true);
        AppUser saved = userRepository.saveAndFlush(user);

        for (Role role : operatorRoles) {
            operatorRoleRepository.save(new OperatorRole(new OperatorRoleId(saved.getId(), role)));
        }
        return saved;
    }

    protected Company createCompany(String name, String inn, CompanyStatus status, AppUser owner) {
        Company company = new Company();
        company.setName(name);
        company.setInn(inn);
        company.setLegalAddress("Тестовый адрес");
        company.setStatus(status);
        company.setConfirmedAt(status == CompanyStatus.ACTIVE
                ? OffsetDateTime.now(ZoneOffset.UTC) : null);
        Company saved = companyRepository.saveAndFlush(company);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompanyId(saved.getId());
        membership.setUserId(owner.getId());
        membership.setAddedAt(OffsetDateTime.now(ZoneOffset.UTC));
        CompanyMembership savedMembership = membershipRepository.saveAndFlush(membership);

        membershipRoleRepository.save(new MembershipRole(
                new MembershipRoleId(savedMembership.getId(), Role.CLIENT_OWNER)));
        return saved;
    }

    // --- HTTP ---

    protected HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        return headers;
    }

    protected HttpHeaders authorizedHeaders(String accessToken, Role activeRole) {
        HttpHeaders headers = internalHeaders();
        headers.setBearerAuth(accessToken);
        if (activeRole != null) {
            headers.set("X-Active-Role", activeRole.name());
        }
        return headers;
    }

    protected ApiResponse request(HttpMethod method, String path, HttpHeaders headers, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(30))
                    .method(method.name(), body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> payload = response.body() == null || response.body().isBlank()
                    ? Map.of()
                    : objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
                    });
            String correlationId = response.headers().firstValue("X-Correlation-Id").orElse(null);
            return new ApiResponse(response.statusCode(), payload, correlationId);
        } catch (Exception ex) {
            throw new AssertionError("HTTP-запрос " + method + " " + path + " не выполнен", ex);
        }
    }

    protected ApiResponse post(String path, String accessToken, Role role, Object body) {
        return request(HttpMethod.POST, path, authorizedHeaders(accessToken, role), body);
    }

    protected ApiResponse get(String path, String accessToken, Role role) {
        return request(HttpMethod.GET, path, authorizedHeaders(accessToken, role), null);
    }

    protected String login(String login) {
        ApiResponse response = request(HttpMethod.POST, "/internal/v1/auth/login",
                internalHeaders(), Map.of("login", login, "password", PASSWORD));
        if (response.status() != 200 || response.body().get("accessToken") == null) {
            throw new AssertionError("Не удалось войти под " + login + ": " + response.status()
                    + " " + response.body());
        }
        return (String) response.body().get("accessToken");
    }

    protected List<Map<String, Object>> auditRows(UUID requestId) {
        return jdbc.queryForList(
                "select action, from_status, to_status, actor_id from audit_log where request_id = ? order by created_at",
                requestId);
    }

    // --- mock-сервисы ---

    protected void stubCrm(String inn, String segment, String assignedManagerLogin) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("inn", inn);
        body.put("name", "Тестовая организация");
        body.put("segment", segment);
        body.put("assignedManagerLogin", assignedManagerLogin);
        body.put("updatedAt", "2026-11-01T09:00:00Z");
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/crm/v1/customers"))
                .withQueryParam("inn", equalTo(inn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(body))));
    }

    protected void stubCrmUnavailable(String inn) {
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/crm/v1/customers"))
                .withQueryParam("inn", equalTo(inn))
                .willReturn(aResponse().withStatus(503)));
    }

    /** CRM отвечает 503 на первые две попытки и 200 на третью («Шаткий» из сида). */
    protected void stubCrmFlaky(String inn, String assignedManagerLogin) {
        String scenario = "crm-flaky-" + inn;
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("inn", inn);
        body.put("segment", "LARGE");
        body.put("assignedManagerLogin", assignedManagerLogin);

        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/crm/v1/customers"))
                .withQueryParam("inn", equalTo(inn))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/crm/v1/customers"))
                .withQueryParam("inn", equalTo(inn))
                .inScenario(scenario)
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("third"));
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/crm/v1/customers"))
                .withQueryParam("inn", equalTo(inn))
                .inScenario(scenario)
                .whenScenarioStateIs("third")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(body))));
    }

    protected void stubBilling(String inn, String debtAmount) {
        Map<String, Object> body = Map.of(
                "inn", inn,
                "debtAmount", debtAmount,
                "currency", "RUB",
                "checkedAt", "2026-11-01T09:00:00Z");
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/billing/v1/accounts/" + inn + "/balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(body))));
    }

    protected void stubRegistry(String inn, boolean govCustomer) {
        Map<String, Object> body = Map.of(
                "inn", inn,
                "kpp", "772501001",
                "ogrn", "1027725012347",
                "name", "Тестовая организация",
                "legalAddress", "Тестовый адрес",
                "status", "ACTIVE",
                "isGovCustomer", govCustomer);
        EXTERNAL_MOCKS.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/registry/v1/organizations"))
                .withQueryParam("inn", equalTo(inn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(body))));
    }

    protected void stubAllExternalSystems(String inn, String segment, String managerLogin) {
        stubCrm(inn, segment, managerLogin);
        stubBilling(inn, "0.00");
        stubRegistry(inn, false);
    }

    protected Map<String, Object> requestPayload() {
        return Map.of(
                "address", "г. Екатеринбург, ул. Ленина, 1",
                "requestedSpeedMbps", 100,
                "desiredDate", "2026-12-01",
                "contactName", "Иван Петров",
                "contactPhone", "+7 999 123-45-67",
                "contractPreference", "OFFER",
                "clientComment", "Подключение для офиса");
    }

    /** Ответ тестового HTTP-клиента. */
    protected record ApiResponse(int status, Map<String, Object> body, String correlationId) {
    }
}
