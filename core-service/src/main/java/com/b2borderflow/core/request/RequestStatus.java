package com.b2borderflow.core.request;

/** Статусы заявки (docs/system/status-model.md, раздел 3). */
public enum RequestStatus {
    DRAFT,
    DATA_CHECK,
    WITH_MANAGER,
    TECH_CHECK,
    IN_APPROVAL,
    REWORK,
    CONTRACT_SIGNING,
    IN_EXECUTION,
    ACTIVATED,
    CANCEL_REQUESTED,
    CLOSED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == CANCELLED;
    }
}
