package com.b2borderflow.core.routing;

/**
 * Фаза вычисления маршрута (docs/system/routing-rules.md, раздел 5).
 * Предварительный маршрут строится на T04, окончательный — после результата ТВ.
 */
public enum RoutingPhase {

    PRELIMINARY,
    FINAL;

    /** Правило фазы {@code rulePhase} доступно при вычислении маршрута фазы {@code phase}. */
    public boolean includes(RoutingPhase rulePhase) {
        return rulePhase.ordinal() <= ordinal();
    }
}
