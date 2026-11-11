package com.b2borderflow.core.company;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Роль пользователя в компании. Роль {@code CLIENT_OWNER} единственная на компанию;
 * сервис и триггер БД не дают назначить вторую.
 */
@Entity
@Table(name = "membership_role")
public class MembershipRole {

    @EmbeddedId
    private MembershipRoleId id;

    protected MembershipRole() {
    }

    public MembershipRole(MembershipRoleId id) {
        this.id = id;
    }

    public MembershipRoleId getId() {
        return id;
    }

    public void setId(MembershipRoleId id) {
        this.id = id;
    }
}
