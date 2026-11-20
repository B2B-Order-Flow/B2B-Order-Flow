package com.b2borderflow.core.request;

/** Типы этапов маршрута (docs/system/status-model.md, 4.1). */
public enum RequestStageType {
    TECH_CHECK,
    RECHECK_TECH,
    LEGAL_APPROVAL,
    FINANCE_APPROVAL,
    PROCUREMENT_APPROVAL,
    MGMT_APPROVAL,
    CONTRACT_SIGNING,
    CONSTRUCTION,
    INSTALLATION,
    ACTIVATION,
    REMEDIATION,
    CLOSING;

    public boolean isApproval() {
        return this == LEGAL_APPROVAL
                || this == FINANCE_APPROVAL
                || this == PROCUREMENT_APPROVAL
                || this == MGMT_APPROVAL;
    }

    /** Первый этап исполнения включает T14 (status-model.md, 6.1.2). */
    public boolean isFirstExecution() {
        return this == CONSTRUCTION || this == INSTALLATION;
    }
}
