package com.b2borderflow.core.company;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Роль сотрудника оператора. Выдаётся миграциями/сидом, не через API. */
@Entity
@Table(name = "operator_role")
public class OperatorRole {

    @EmbeddedId
    private OperatorRoleId id;

    protected OperatorRole() {
    }

    public OperatorRole(OperatorRoleId id) {
        this.id = id;
    }

    public OperatorRoleId getId() {
        return id;
    }

    public void setId(OperatorRoleId id) {
        this.id = id;
    }
}
