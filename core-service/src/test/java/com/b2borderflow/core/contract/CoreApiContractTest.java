package com.b2borderflow.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

    /** Операции П2: уже есть в шлюзе, добавляются в контракт ядра. */
    private static final Map<String, String[]> P2_OPERATIONS = Map.ofEntries(
            Map.entry("/internal/v1/requests/{requestId}/from-rejected", new String[]{"post", "FollowUp"}),
            Map.entry("/internal/v1/archive/requests", new String[]{"get", "Archive"}),
            Map.entry("/internal/v1/exports/requests", new String[]{"post", "Archive"}),
            Map.entry("/internal/v1/exports/{exportId}", new String[]{"get", "Archive"}));

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

    @Test
    void p2OperationsAreDeclaredWithGatewayTags() {
        for (Map.Entry<String, String[]> entry : P2_OPERATIONS.entrySet()) {
            String method = entry.getValue()[0];
            String expectedTag = entry.getValue()[1];
            Map<String, Object> operation = operation(entry.getKey(), method);
            assertNotNull(operation.get("operationId"), "У операции " + method.toUpperCase()
                    + " " + entry.getKey() + " нет operationId");
            List<String> tags = asList(operation.get("tags"),
                    entry.getKey() + " " + method + ": tags");
            assertEquals(List.of(expectedTag), tags, "Операция " + method.toUpperCase() + " "
                    + entry.getKey() + " должна быть в теге " + expectedTag
                    + ": иначе она попадёт в существующий интерфейс и сломает контроллер");
        }
    }

    /**
     * Справочники — единственная операция недели 3 с query-параметром. Без проверки
     * параметра и схемы ответа контрактный тест не заметит, что, например, `stageType`
     * потерялся при правке YAML.
     */
    @Test
    void dictionaryEndpointExposesStageTypeAndResponseSchema() {
        Map<String, Object> stageType = parameter("/internal/v1/dictionaries/{name}", "get", "stageType");
        assertEquals("query", stageType.get("in"), "stageType должен быть query-параметром");
        assertEquals(Boolean.FALSE, stageType.get("required"), "stageType необязателен");
        assertEquals("#/components/schemas/StageType",
                asMap(stageType.get("schema"), "Dictionary.stageType.schema").get("$ref"),
                "stageType должен ссылаться на общий StageType");
        assertEquals("#/components/schemas/DictionaryView",
                responseSchemaRef("/internal/v1/dictionaries/{name}", "get", "200"),
                "Ответ справочника должен быть DictionaryView");

        Set<Object> stageTypes = new HashSet<>(asList(schema("StageType").get("enum"), "StageType.enum"));
        assertTrue(stageTypes.containsAll(Set.of(
                        "TECH_CHECK", "RECHECK_TECH", "LEGAL_APPROVAL",
                        "FINANCE_APPROVAL", "PROCUREMENT_APPROVAL", "MGMT_APPROVAL")),
                "StageType должен содержать шаги ТВ и все виды согласований: " + stageTypes);

        assertEquals(Set.of("pause-reasons", "cancel-reasons", "rejection-reasons",
                        "contract-types", "attachment-kinds"),
                new HashSet<>(asList(schema("DictionaryName").get("enum"), "DictionaryName.enum")),
                "DictionaryName ограничен известными справочниками");

        assertEquals(Set.of("name", "items"),
                new HashSet<>(asList(schema("DictionaryView").get("required"), "DictionaryView.required")));
        assertEquals(Set.of("code", "title"),
                new HashSet<>(asList(schema("DictionaryItem").get("required"), "DictionaryItem.required")),
                "У значения справочника обязательны code и title");

        Map<String, Object> items = asMap(properties("DictionaryView").get("items"), "DictionaryView.items");
        assertEquals("array", items.get("type"));
        assertEquals("#/components/schemas/DictionaryItem",
                asMap(items.get("items"), "DictionaryView.items.items").get("$ref"));
    }

    @Test
    void routeStageKeepsRequiredByRulesOptionalForClients() {
        List<Object> required = asList(schema("RouteStageView").get("required"), "RouteStageView.required");
        assertFalse(required.contains("requiredByRules"),
                "requiredByRules не отдаётся клиенту, поэтому не может быть обязательным");
        assertTrue(required.containsAll(Set.of("type", "wave")));
        Map<String, Object> property = asMap(properties("RouteStageView").get("requiredByRules"),
                "RouteStageView.requiredByRules");
        assertTrue(String.valueOf(property.get("description")).contains("не отдаётся клиенту"),
                "В описании requiredByRules должно быть указано, что клиенту поле не отдаётся");
    }

    @Test
    void stageSlaSummaryContainsConfirmedFields() {
        Map<String, Object> properties = properties("StageSlaSummary");
        assertTrue(properties.keySet().containsAll(Set.of("status", "dueAt", "remainingMinutes", "overdue")),
                "Сводка SLA должна содержать status, dueAt, remainingMinutes и overdue");
        Map<String, Object> remainingMinutes = asMap(properties.get("remainingMinutes"),
                "StageSlaSummary.remainingMinutes");
        assertTrue(String.valueOf(remainingMinutes.get("description")).contains("календар"),
                "Остаток считается по календарю конкретного таймера");
    }

    @Test
    void p2SchemasDefineRequestAndResponse() {
        assertEquals(Set.of("format"),
                new HashSet<>(asList(schema("ExportRequestPayload").get("required"),
                        "ExportRequestPayload.required")));
        assertEquals(Set.of("id", "format", "status", "createdAt"),
                new HashSet<>(asList(schema("ExportJobView").get("required"),
                        "ExportJobView.required")));
        assertEquals(Set.of("CSV", "PDF"),
                new HashSet<>(asList(schema("ExportFormat").get("enum"), "ExportFormat.enum")));
        assertEquals("#/components/schemas/RequestView",
                responseSchemaRef("/internal/v1/requests/{requestId}/from-rejected", "post", "201"),
                "Новая заявка на основе отклонённой возвращает RequestView");
    }

    /**
     * Шлюз валидирует тело своим DTO. Если здесь `breakdown` станет объектом или
     * строкой, BFF начнёт отклонять валидный запрос — тест должен ловить это первым.
     */
    @Test
    void cancellationCostsBreakdownIsArrayOfItems() {
        Map<String, Object> breakdown = asMap(properties("CancellationCostsPayload").get("breakdown"),
                "CancellationCostsPayload.breakdown");
        assertEquals("array", breakdown.get("type"), "breakdown должен быть массивом");
        assertEquals(1, ((Number) breakdown.get("minItems")).intValue(), "breakdown не может быть пустым");
        assertEquals("#/components/schemas/CancellationCostItem",
                asMap(breakdown.get("items"), "CancellationCostsPayload.breakdown.items").get("$ref"));

        assertEquals(Set.of("description", "amount"),
                new HashSet<>(asList(schema("CancellationCostItem").get("required"),
                        "CancellationCostItem.required")));
        assertMinLength("CancellationCostItem", "description");
        assertEquals("^[0-9]+\\.[0-9]{2}$",
                asMap(properties("CancellationCostItem").get("amount"),
                        "CancellationCostItem.amount").get("pattern"));
    }

    @Test
    void requiredTextFieldsRejectBlankStrings() {
        Map<String, List<String>> fields = Map.ofEntries(
                Map.entry("StageDecisionPayload", List.of("comment", "reasonCode")),
                Map.entry("StageReturnPayload", List.of("comment")),
                Map.entry("StageProblemPayload", List.of("comment")),
                Map.entry("ContractChangeRequestPayload", List.of("comment")),
                Map.entry("DefectClaimPayload", List.of("reason")),
                Map.entry("CancellationReasonPayload", List.of("reasonCode")),
                Map.entry("CancellationRejectPayload", List.of("comment")),
                Map.entry("CancellationCostItem", List.of("description")));
        for (Map.Entry<String, List<String>> entry : fields.entrySet()) {
            for (String field : entry.getValue()) {
                assertMinLength(entry.getKey(), field);
            }
        }
    }

    @Test
    void commandRequiredFieldsAreDeclared() {
        Map<String, Set<String>> required = Map.ofEntries(
                Map.entry("StageDecisionPayload", Set.of("decision", "version")),
                Map.entry("StageReturnPayload", Set.of("comment", "version")),
                Map.entry("StageProblemPayload", Set.of("problem", "comment", "version")),
                Map.entry("ContractChangeRequestPayload", Set.of("comment", "version")),
                Map.entry("DefectClaimPayload", Set.of("reason", "version")),
                Map.entry("CancellationCostsPayload", Set.of("amount", "breakdown", "version")),
                Map.entry("ConfirmCostsPayload", Set.of("confirmed", "version")),
                Map.entry("CancellationRejectPayload", Set.of("comment", "version")),
                Map.entry("UpdateRequestDataPayload", Set.of("version")));
        for (Map.Entry<String, Set<String>> entry : required.entrySet()) {
            assertEquals(entry.getValue(), required(entry.getKey()),
                    "Обязательные поля " + entry.getKey() + " не совпадают с контрактом");
        }
    }

    @Test
    void confirmCostsDocumentsConditionalComment() {
        String description = String.valueOf(schema("ConfirmCostsPayload").get("description"));
        assertTrue(description.contains("confirmed = false"),
                "Описание должно фиксировать условную обязательность comment");
        Map<String, Object> comment = asMap(properties("ConfirmCostsPayload").get("comment"),
                "ConfirmCostsPayload.comment");
        assertTrue(String.valueOf(comment.get("description")).contains("confirmed = false"));
        assertMinLength("ConfirmCostsPayload", "comment");
    }

    /**
     * Аналитика задаёт `status` и `segment` списками (archive-export.md, 4).
     * В контракте и в шлюзе они должны быть массивами, а не одиночными строками.
     */
    @Test
    void archiveFiltersFollowAnalyticsLists() {
        Map<String, Object> status = parameter("/internal/v1/archive/requests", "get", "status");
        Map<String, Object> statusSchema = asMap(status.get("schema"), "archive.status.schema");
        assertEquals("array", statusSchema.get("type"));
        assertEquals("#/components/schemas/RequestStatus",
                asMap(statusSchema.get("items"), "archive.status.items").get("$ref"));
        assertEquals("form", status.get("style"));
        assertEquals(Boolean.TRUE, status.get("explode"));

        Map<String, Object> segment = parameter("/internal/v1/archive/requests", "get", "segment");
        Map<String, Object> segmentSchema = asMap(segment.get("schema"), "archive.segment.schema");
        assertEquals("array", segmentSchema.get("type"));
        assertEquals("string", asMap(segmentSchema.get("items"), "archive.segment.items").get("type"));

        assertEquals("array", asMap(properties("ExportFilters").get("status"),
                "ExportFilters.status").get("type"));
        assertEquals("array", asMap(properties("ExportFilters").get("segment"),
                "ExportFilters.segment").get("type"));
    }

    @Test
    void archiveDeclaresPageAndSize() {
        for (String name : List.of("page", "size")) {
            Map<String, Object> parameter = parameter("/internal/v1/archive/requests", "get", name);
            assertEquals("query", parameter.get("in"));
            assertEquals(Boolean.FALSE, parameter.get("required"));
            assertEquals("integer", asMap(parameter.get("schema"), "archive." + name + ".schema").get("type"));
        }
        Map<String, Object> size = asMap(parameter("/internal/v1/archive/requests", "get", "size").get("schema"),
                "archive.size.schema");
        assertEquals(100, ((Number) size.get("maximum")).intValue());
    }

    @Test
    void updateRequestDataSupportsMergeSemantics() {
        String description = String.valueOf(schema("UpdateRequestDataPayload").get("description"));
        assertTrue(description.contains("поле пропущено"), "Должно быть описано сохранение пропущенного поля");
        assertTrue(description.contains("явный `null`"), "Должна быть описана очистка через null");
        assertTrue(description.contains("T10"), "Должна быть ссылка на обязательность полей при T10");

        for (String field : List.of("desiredDate", "contactName", "contactPhone", "discountPercent")) {
            Map<String, Object> property = asMap(properties("UpdateRequestDataPayload").get(field),
                    "UpdateRequestDataPayload." + field);
            assertEquals(Boolean.TRUE, property.get("nullable"),
                    field + " должен допускать явный null для очистки");
        }
        for (String field : List.of("address", "requestedSpeedMbps", "tariffType", "contractType")) {
            Map<String, Object> property = asMap(properties("UpdateRequestDataPayload").get(field),
                    "UpdateRequestDataPayload." + field);
            assertFalse(Boolean.TRUE.equals(property.get("nullable")),
                    field + " нельзя очищать через null");
        }

        String discount = String.valueOf(asMap(properties("UpdateRequestDataPayload").get("discountPercent"),
                "UpdateRequestDataPayload.discountPercent").get("description"));
        assertTrue(discount.contains("null"), "Для скидки должен быть описан явный null");
        assertTrue(discount.contains("пропущенное"), "Для скидки должно быть описано сохранение при пропуске");
    }

    @Test
    void weekThreeOperationsDocumentResponseSchemas() {
        assertEquals("#/components/schemas/StageView",
                responseSchemaRef("/internal/v1/stages/{stageId}/decision", "post", "200"));
        assertEquals("#/components/schemas/StageView",
                responseSchemaRef("/internal/v1/stages/{stageId}/return", "post", "200"));
        assertEquals("#/components/schemas/CancellationRequestView",
                responseSchemaRef("/internal/v1/cancellation-requests/{cancellationId}/costs", "post", "200"));
        assertEquals("#/components/schemas/CancellationRequestView",
                responseSchemaRef("/internal/v1/cancellation-requests/{cancellationId}/approve", "post", "200"));
        assertEquals("#/components/schemas/RequestPage",
                responseSchemaRef("/internal/v1/archive/requests", "get", "200"));
        assertEquals("#/components/schemas/ExportJobView",
                responseSchemaRef("/internal/v1/exports/requests", "post", "202"));
        assertEquals("#/components/schemas/ExportJobView",
                responseSchemaRef("/internal/v1/exports/{exportId}", "get", "200"));
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

    private static Map<String, Object> schema(String name) {
        Map<String, Object> components = asMap(document.get("components"), "components");
        Map<String, Object> schemas = asMap(components.get("schemas"), "components.schemas");
        return asMap(schemas.get(name), "components.schemas." + name);
    }

    private static Map<String, Object> properties(String schemaName) {
        Map<String, Object> merged = new HashMap<>();
        collectProperties(schema(schemaName), merged, "components.schemas." + schemaName);
        return merged;
    }

    private static void collectProperties(Map<String, Object> schema, Map<String, Object> target, String what) {
        Object reference = schema.get("$ref");
        if (reference instanceof String ref && ref.startsWith("#/components/schemas/")) {
            String name = ref.substring("#/components/schemas/".length());
            collectProperties(schema(name), target, ref);
        }
        Object properties = schema.get("properties");
        if (properties != null) {
            target.putAll(asMap(properties, what + ".properties"));
        }
        Object allOf = schema.get("allOf");
        if (allOf != null) {
            for (Object part : asList(allOf, what + ".allOf")) {
                collectProperties(asMap(part, what + ".allOf[]"), target, what + ".allOf[]");
            }
        }
    }

    private static Set<Object> required(String schemaName) {
        Set<Object> merged = new HashSet<>();
        collectRequired(schema(schemaName), merged);
        return merged;
    }

    private static void collectRequired(Map<String, Object> schema, Set<Object> target) {
        Object reference = schema.get("$ref");
        if (reference instanceof String ref && ref.startsWith("#/components/schemas/")) {
            collectRequired(schema(ref.substring("#/components/schemas/".length())), target);
        }
        Object required = schema.get("required");
        if (required != null) {
            target.addAll(asList(required, "required"));
        }
        Object allOf = schema.get("allOf");
        if (allOf != null) {
            for (Object part : asList(allOf, "allOf")) {
                collectRequired(asMap(part, "allOf[]"), target);
            }
        }
    }

    private static void assertMinLength(String schemaName, String field) {
        Map<String, Object> property = asMap(properties(schemaName).get(field),
                schemaName + "." + field);
        assertEquals(1, ((Number) property.get("minLength")).intValue(),
                schemaName + "." + field + " должен запрещать пустую строку");
    }

    private static Map<String, Object> parameter(String path, String method, String name) {
        List<Map<String, Object>> parameters = asList(operation(path, method).get("parameters"),
                path + " " + method + ": parameters");
        for (Map<String, Object> parameter : parameters) {
            Map<String, Object> resolved = resolveParameter(parameter);
            if (name.equals(resolved.get("name"))) {
                return resolved;
            }
        }
        return fail("У " + method.toUpperCase() + " " + path + " нет параметра " + name);
    }

    private static Map<String, Object> resolveParameter(Map<String, Object> parameter) {
        Object reference = parameter.get("$ref");
        if (reference instanceof String ref && ref.startsWith("#/components/parameters/")) {
            Map<String, Object> components = asMap(document.get("components"), "components");
            Map<String, Object> parameters = asMap(components.get("parameters"), "components.parameters");
            return asMap(parameters.get(ref.substring("#/components/parameters/".length())), ref);
        }
        return parameter;
    }

    private static String responseSchemaRef(String path, String method, String status) {
        Map<String, Object> responses = asMap(operation(path, method).get("responses"),
                path + " " + method + ": responses");
        Map<String, Object> response = asMap(responses.get(status),
                path + " " + method + ": response " + status);
        Map<String, Object> content = asMap(response.get("content"),
                path + " " + method + " " + status + ": content");
        Map<String, Object> media = asMap(content.get("application/json"),
                path + " " + method + " " + status + ": application/json");
        return (String) asMap(media.get("schema"),
                path + " " + method + " " + status + ": schema").get("$ref");
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
