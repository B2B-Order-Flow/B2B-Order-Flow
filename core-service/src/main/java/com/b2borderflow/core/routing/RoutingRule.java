package com.b2borderflow.core.routing;

import com.b2borderflow.core.request.ContractType;
import com.b2borderflow.core.request.TariffType;
import com.b2borderflow.core.request.TechResult;
import java.math.BigDecimal;
import java.util.function.BiPredicate;

/**
 * Реестр правил маршрутизации R-01…R-08 (docs/system/routing-rules.md, раздел 5).
 * Одно правило — одна строка таблицы: условие, вид согласования и фаза вычисления.
 * Правила не обращаются к БД: пороги приходят в {@link RoutingThresholds}.
 */
public enum RoutingRule {

    R01("R-01", ApprovalType.LEGAL, RoutingPhase.PRELIMINARY,
            (parameters, thresholds) -> parameters.contractType() == ContractType.CLIENT_FORM),

    R02("R-02", ApprovalType.LEGAL, RoutingPhase.PRELIMINARY,
            (parameters, thresholds) -> Boolean.TRUE.equals(parameters.govCustomer())),

    R03("R-03", ApprovalType.FINANCE, RoutingPhase.PRELIMINARY,
            (parameters, thresholds) -> parameters.tariffType() == TariffType.INDIVIDUAL
                    || money(parameters.discountPercent()).signum() > 0),

    R04("R-04", ApprovalType.FINANCE, RoutingPhase.PRELIMINARY,
            (parameters, thresholds) -> money(parameters.debtAmount())
                    .compareTo(thresholds.debtThresholdRub()) > 0),

    R05("R-05", ApprovalType.FINANCE, RoutingPhase.FINAL,
            (parameters, thresholds) -> parameters.techResult() == TechResult.WITH_CONSTRUCTION),

    R06("R-06", ApprovalType.PROCUREMENT, RoutingPhase.FINAL,
            (parameters, thresholds) -> parameters.techResult() == TechResult.WITH_CONSTRUCTION),

    R07("R-07", ApprovalType.MANAGEMENT, RoutingPhase.PRELIMINARY,
            (parameters, thresholds) -> parameters.segment() != null
                    && thresholds.strategicSegments().contains(parameters.segment())),

    R08("R-08", ApprovalType.MANAGEMENT, RoutingPhase.FINAL,
            (parameters, thresholds) -> money(parameters.capex())
                    .compareTo(thresholds.capexMgmtThresholdRub()) > 0);

    private final String code;
    private final ApprovalType approvalType;
    private final RoutingPhase phase;
    private final BiPredicate<RoutingParameters, RoutingThresholds> condition;

    RoutingRule(
            String code,
            ApprovalType approvalType,
            RoutingPhase phase,
            BiPredicate<RoutingParameters, RoutingThresholds> condition) {
        this.code = code;
        this.approvalType = approvalType;
        this.phase = phase;
        this.condition = condition;
    }

    /** ID правила в виде, в котором он хранится в {@code request_stage.required_by_rules}. */
    public String code() {
        return code;
    }

    public ApprovalType approvalType() {
        return approvalType;
    }

    public RoutingPhase phase() {
        return phase;
    }

    public boolean applies(RoutingParameters parameters, RoutingThresholds thresholds) {
        return condition.test(parameters, thresholds);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
