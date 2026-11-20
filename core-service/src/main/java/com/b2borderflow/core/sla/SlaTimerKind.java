package com.b2borderflow.core.sla;

/**
 * Вид таймера SLA (docs/system/api-contract.md, 10.10; docs/system/sla.md, раздел 8).
 * Те же значения используются в {@code notification.payload.deadlineKind}, чтобы
 * интерфейсу не приходилось сопоставлять два набора имён.
 */
public enum SlaTimerKind {

    STAGE_SLA,
    DATA_CHECK,
    MANAGER_ASSIGNMENT,
    TECH_RESULT,
    ACCEPTANCE,
    CANCEL_RESPONSE;

    public boolean isStage() {
        return this == STAGE_SLA;
    }
}
