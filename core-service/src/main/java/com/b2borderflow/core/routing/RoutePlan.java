package com.b2borderflow.core.routing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Результат вычисления маршрута (docs/system/routing-rules.md, 6.2): сработавшие правила,
 * волны согласований и признак этапа «Стройка». Маршрут — объединение результатов правил.
 */
public record RoutePlan(List<RoutingRule> rules, boolean construction) {

    public RoutePlan {
        rules = List.copyOf(rules);
    }

    public List<RoutingRule> rulesFor(ApprovalType approvalType) {
        return rules.stream()
                .filter(rule -> rule.approvalType() == approvalType)
                .toList();
    }

    public boolean requires(ApprovalType approvalType) {
        return rules.stream().anyMatch(rule -> rule.approvalType() == approvalType);
    }

    public boolean requiresApprovals() {
        return !rules.isEmpty();
    }

    /** Виды согласований маршрута в порядке волн и объявления {@link ApprovalType}. */
    public Set<ApprovalType> approvalTypes() {
        EnumSet<ApprovalType> result = EnumSet.noneOf(ApprovalType.class);
        rules.forEach(rule -> result.add(rule.approvalType()));
        return Collections.unmodifiableSet(result);
    }

    /** Волна 1 — профильные согласования, выполняются параллельно. */
    public List<ApprovalType> wave1() {
        return approvalTypes().stream()
                .filter(ApprovalType::isProfile)
                .sorted()
                .toList();
    }

    /** Волна 2 — управленческое согласование, после профильных. */
    public List<ApprovalType> wave2() {
        return requires(ApprovalType.MANAGEMENT) ? List.of(ApprovalType.MANAGEMENT) : List.of();
    }

    /** Значения для {@code request_stage.required_by_rules}: объяснение, почему возник этап. */
    public List<String> requiredByRules() {
        return rules.stream().map(RoutingRule::code).toList();
    }

    /** Класс заявки — производный признак, а не вход правила (routing-rules.md, раздел 8). */
    public RequestClass requestClass() {
        if (!requiresApprovals()) {
            return RequestClass.STANDARD;
        }
        boolean large = requires(ApprovalType.PROCUREMENT) || requires(ApprovalType.MANAGEMENT);
        return large ? RequestClass.LARGE : RequestClass.EXTENDED;
    }
}
