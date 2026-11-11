package com.b2borderflow.core.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Итог вызова одной внешней системы. {@code error == null} — система ответила
 * (включая бизнес-ответ 404); иначе это технический сбой после всех попыток.
 */
public record ExternalCallResult<T>(
        T data,
        int httpStatus,
        Map<String, Object> rawPayload,
        int attempts,
        String error) {

    public ExternalCallResult {
        rawPayload = rawPayload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawPayload));
    }

    public boolean ok() {
        return error == null;
    }

    public boolean notFound() {
        return httpStatus == 404;
    }

    public <R> ExternalCallResult<R> mapData(Function<T, R> mapper) {
        return new ExternalCallResult<>(
                ok() ? mapper.apply(data) : null,
                httpStatus,
                rawPayload,
                attempts,
                error);
    }

    public static <T> ExternalCallResult<T> success(T data, int httpStatus,
                                                    Map<String, Object> rawPayload, int attempts) {
        return new ExternalCallResult<>(data, httpStatus, rawPayload, attempts, null);
    }

    public static <T> ExternalCallResult<T> failure(int httpStatus, Map<String, Object> rawPayload,
                                                    int attempts, String error) {
        return new ExternalCallResult<>(null, httpStatus, rawPayload, attempts, error);
    }
}
