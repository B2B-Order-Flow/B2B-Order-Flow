package com.b2borderflow.core.integration;

import java.math.BigDecimal;
import java.util.Map;

/** Чтение типизированных значений из JSONB-полезной нагрузки mock-ответа. */
public final class ExternalPayload {

    private ExternalPayload() {
    }

    public static String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    public static boolean bool(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public static BigDecimal decimal(Map<String, Object> payload, String key, BigDecimal fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int intValue(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
