package com.b2borderflow.core.logging;

import org.slf4j.MDC;

/**
 * Сквозной идентификатор запроса. Устанавливается фильтром в MDC и читается
 * в любом слое, включая записи аудита и вызовы mock-сервисов.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 128;

    private CorrelationId() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /** Значение из заголовка принимается только безопасной длины и без управляющих символов. */
    public static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                return null;
            }
        }
        return trimmed;
    }
}
