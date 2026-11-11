package com.b2borderflow.core.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Успешный HTTP-ответ mock-сервиса, включая {@code 404}: для интеграций
 * «не найдено» — нормальный бизнес-ответ, а не сбой.
 */
public record ExternalRawResponse(int httpStatus, Map<String, Object> payload) {

    public ExternalRawResponse {
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
