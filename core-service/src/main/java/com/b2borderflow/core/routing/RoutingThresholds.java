package com.b2borderflow.core.routing;

import com.b2borderflow.core.request.Segment;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Пороги правил маршрутизации (docs/system/routing-rules.md, раздел 4).
 * Значения по умолчанию совпадают с миграцией {@code routing_config}; класс умеет
 * прочитать пороги из строк таблицы {@code routing_config} без обращения к БД.
 */
public record RoutingThresholds(
        int standardSpeedMaxMbps,
        BigDecimal debtThresholdRub,
        BigDecimal capexMgmtThresholdRub,
        Set<Segment> strategicSegments) {

    public static final int DEFAULT_STANDARD_SPEED_MAX_MBPS = 100;
    public static final BigDecimal DEFAULT_DEBT_THRESHOLD_RUB = new BigDecimal("0");
    public static final BigDecimal DEFAULT_CAPEX_MGMT_THRESHOLD_RUB = new BigDecimal("500000");
    public static final Set<Segment> DEFAULT_STRATEGIC_SEGMENTS = Set.of(Segment.STRATEGIC);

    public static final String KEY_STANDARD_SPEED_MAX_MBPS = "standard_speed_max_mbps";
    public static final String KEY_DEBT_THRESHOLD_RUB = "debt_threshold_rub";
    public static final String KEY_CAPEX_MGMT_THRESHOLD_RUB = "capex_mgmt_threshold_rub";
    public static final String KEY_STRATEGIC_SEGMENTS = "strategic_segments";

    public RoutingThresholds {
        Objects.requireNonNull(debtThresholdRub, "debtThresholdRub");
        Objects.requireNonNull(capexMgmtThresholdRub, "capexMgmtThresholdRub");
        Objects.requireNonNull(strategicSegments, "strategicSegments");
        strategicSegments = Set.copyOf(strategicSegments);
    }

    public static RoutingThresholds defaults() {
        return new RoutingThresholds(
                DEFAULT_STANDARD_SPEED_MAX_MBPS,
                DEFAULT_DEBT_THRESHOLD_RUB,
                DEFAULT_CAPEX_MGMT_THRESHOLD_RUB,
                DEFAULT_STRATEGIC_SEGMENTS);
    }

    /**
     * Читает пороги из значений {@code routing_config} ({@code config_key -> config_value}).
     * Значения — уже разобранный JSONB ({@code Number}, {@code List}, {@code String}).
     * Отсутствующий ключ даёт значение по умолчанию, опечатка в значении —
     * {@link IllegalArgumentException}: молча подменять порог нельзя.
     */
    public static RoutingThresholds fromValues(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        Object speed = values.get(KEY_STANDARD_SPEED_MAX_MBPS);
        Object debt = values.get(KEY_DEBT_THRESHOLD_RUB);
        Object capex = values.get(KEY_CAPEX_MGMT_THRESHOLD_RUB);
        Object segments = values.get(KEY_STRATEGIC_SEGMENTS);

        return new RoutingThresholds(
                intValue(speed, DEFAULT_STANDARD_SPEED_MAX_MBPS),
                decimalValue(debt, DEFAULT_DEBT_THRESHOLD_RUB),
                decimalValue(capex, DEFAULT_CAPEX_MGMT_THRESHOLD_RUB),
                segments == null ? DEFAULT_STRATEGIC_SEGMENTS : readSegments(segments));
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(
                "routing_config." + KEY_STANDARD_SPEED_MAX_MBPS + " должен быть числом: " + value);
    }

    private static BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new IllegalArgumentException("Порог routing_config должен быть числом: " + value);
    }

    private static Set<Segment> readSegments(Object value) {
        if (!(value instanceof List<?> items)) {
            throw new IllegalArgumentException("routing_config.strategic_segments должен быть массивом сегментов");
        }
        Set<Segment> result = EnumSet.noneOf(Segment.class);
        for (Object item : items) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException(
                        "routing_config.strategic_segments содержит не строку: " + item);
            }
            result.add(Segment.valueOf(text));
        }
        return result;
    }
}
