package com.b2borderflow.core.routing;

import com.b2borderflow.core.request.TechResult;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Вычисление маршрута из параметров (docs/system/routing-rules.md, 6.2).
 * Чистая функция: ни БД, ни времени, ни состояния — результат зависит только от
 * параметров, порогов и фазы.
 */
public final class RoutePlanner {

    private RoutePlanner() {
    }

    /**
     * Строит маршрут на заданной фазе.
     *
     * @param parameters параметры заявки и предложения
     * @param thresholds пороги из {@code routing_config}
     * @param phase      предварительная (T04) или окончательная (результат ТВ)
     */
    public static RoutePlan buildRoute(
            RoutingParameters parameters,
            RoutingThresholds thresholds,
            RoutingPhase phase) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(thresholds, "thresholds");
        Objects.requireNonNull(phase, "phase");

        List<RoutingRule> fired = Arrays.stream(RoutingRule.values())
                .filter(rule -> phase.includes(rule.phase()))
                .filter(rule -> rule.applies(parameters, thresholds))
                .toList();

        boolean construction = parameters.techResult() == TechResult.WITH_CONSTRUCTION;
        return new RoutePlan(fired, construction);
    }
}
