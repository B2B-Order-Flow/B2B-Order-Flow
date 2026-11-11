package com.b2borderflow.core.domain;

/**
 * Роли платформы. Клиентские роли относятся к компании-клиенту, операторские —
 * к сотруднику оператора; одна роль может быть только в одной группе.
 */
public enum Role {

    // Компания-клиент
    CLIENT_OWNER(true),
    CLIENT_ADMIN(true),
    CLIENT_REQUESTER(true),

    // Оператор
    SALES_MANAGER(false),
    TECH_SPECIALIST(false),
    LAWYER(false),
    FINANCE_CONTROLLER(false),
    PROCUREMENT_SPECIALIST(false),
    B2B_HEAD(false),
    ENGINEER(false),
    COORDINATOR(false),
    ADMIN(false);

    private final boolean clientRole;

    Role(boolean clientRole) {
        this.clientRole = clientRole;
    }

    public boolean isClientRole() {
        return clientRole;
    }

    public boolean isOperatorRole() {
        return !clientRole;
    }
}
