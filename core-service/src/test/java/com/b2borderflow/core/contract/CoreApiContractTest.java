package com.b2borderflow.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Проверки внутреннего контракта ядра без запуска Spring: состав операций недели 3
 * и защита служебных каналов. Контракт читается как ресурс из classpath.
 */
class CoreApiContractTest {

    /** Операции недели 3, которых не было в контракте 0.2.0. */
    private static final Map<String, String[]> WEEK_THREE_OPERATIONS = Map.ofEntries(
            Map.entry("/internal/v1/requests/{requestId}/data", new String[]{"patch"}),
            Map.entry("/internal/v1/requests/{requestId}/resubmit", new String[]{"post"}),
            Map.entry("/internal/v1/requests/{requestId}/revisions", new String[]{"get"}),
            Map.entry("/internal/v1/requests/{requestId}/route", new String[]{"get"}),
            Map.entry("/internal/v1/stages/{stageId}/decision", new String[]{"post"}),
            Map.entry("/internal/v1/stages/{stageId}/return", new String[]{"post"}),
            Map.entry("/internal/v1/stages/{stageId}/problem", new String[]{"post"}),
            Map.entry("/internal/v1/requests/{requestId}/contract/change-request", new String[]{"post"}),
            Map.entry("/internal/v1/requests/{requestId}/defect-claim", new String[]{"post"}),
            Map.entry("/internal/v1/requests/{requestId}/cancel", new String[]{"post"}),
            Map.entry("/internal/v1/requests/{requestId}/cancellation-requests", new String[]{"get", "post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/costs", new String[]{"post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/confirm-costs", new String[]{"post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/approve", new String[]{"post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/accept", new String[]{"post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/reject", new String[]{"post"}),
            Map.entry("/internal/v1/cancellation-requests/{cancellationId}/withdraw", new String[]{"post"}),
            Map.entry("/internal/v1/dictionaries/{name}", new String[]{"get"}));

    private static final Set<String> EXISTING_API_TAGS = Set.of(
            "Auth", "Companies", "Admin", "Requests", "Workflow",
            "Stages", "Sla", "Notifications", "Events", "Users");

    private static final Set<String> WEEK_THREE_TAGS = Set.of(
            "Rework", "Approvals", "Execution", "Contract", "Cancellation", "Dictionaries");

    private static Map<String, Object> document;

    @BeforeAll
    static void loadContract() {
        try (InputStream input = CoreApiContractTest.class.getResourceAsStream("/openapi/core-api.yaml")) {
            assertNotNull(input, "core-api.yaml должен лежать в src/main/resources/openapi");
            document = asMap(new Yaml().load(input), "core-api.yaml");
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать core-api.yaml", ex);
        }
    }

    @Test
    void weekThreeOperationsAreDeclared() {
        Map<String, Object> paths = asMap(document.get("paths"), "paths");
        for (Map.Entry<String, String[]> entry : WEEK_THREE_OPERATIONS.entrySet()) {
            Map<String, Object> path = asMap(paths.get(entry.getKey()), entry.getKey());
            for (String method : entry.getValue()) {
                Map<String, Object> operation = asMap(path.get(method), entry.getKey() + " " + method);
                assertNotNull(operation.get("operationId"),
                        "У операции " + method.toUpperCase() + " " + entry.getKey() + " нет operationId");
            }
        }
    }

    /**
     * Новые операции должны жить в новых тегах: генератор создаёт по интерфейсу на тег,
     * и добавление метода в существующий RequestsApi/StagesApi сломало бы контроллеры.
     */
    @Test
    void weekThreeOperationsUseOnlyNewTags() {
        Set<String> usedTags = new HashSet<>();
        for (Map.Entry<String, String[]> entry : WEEK_THREE_OPERATIONS.entrySet()) {
            for (String method : entry.getValue()) {
                List<String> tags = asList(operation(entry.getKey(), method).get("tags"),
                        entry.getKey() + " " + method + ": tags");
                assertEquals(1, tags.size(), "У операции должен быть ровно один тег: "
                        + method.toUpperCase() + " " + entry.getKey());
                assertFalse(EXISTING_API_TAGS.contains(tags.get(0)),
                        "Операция " + method.toUpperCase() + " " + entry.getKey()
                                + " добавлена в существующий тег " + tags.get(0)
                                + " — это сломает реализующий его контроллер");
                usedTags.add(tags.get(0));
            }
        }
        assertEquals(WEEK_THREE_TAGS, usedTags,
                "Новые операции должны быть распределены по тегам недели 3");
    }

    /**
     * Канал событий и проверку билета вызывает только шлюз: пользовательского JWT у них
     * нет, поэтому глобальная схема (internalToken + bearerAuth) здесь не подходит.
     */
    @Test
    void internalServiceChannelsRequireOnlyInternalToken() {
        assertInternalTokenOnly("/internal/v1/events/stream", "get");
        assertInternalTokenOnly("/internal/v1/ws/ticket/redeem", "post");
    }

    private static void assertInternalTokenOnly(String path, String method) {
        List<Map<String, Object>> security = asList(operation(path, method).get("security"),
                path + " " + method + ": security");
        assertEquals(1, security.size(), "У " + method.toUpperCase() + " " + path
                + " должна быть ровно одна схема безопасности");
        assertEquals(Set.of("internalToken"), security.get(0).keySet(),
                "У " + method.toUpperCase() + " " + path + " допускается только X-Internal-Token");
    }

    private static Map<String, Object> operation(String path, String method) {
        Map<String, Object> paths = asMap(document.get("paths"), "paths");
        return asMap(asMap(paths.get(path), path).get(method), path + " " + method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String what) {
        assertTrue(value instanceof Map<?, ?>, "Ожидался объект YAML: " + what);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asList(Object value, String what) {
        assertTrue(value instanceof List<?>, "Ожидался список YAML: " + what);
        return (List<T>) value;
    }
}
