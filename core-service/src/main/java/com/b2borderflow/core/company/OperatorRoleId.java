package com.b2borderflow.core.company;

import com.b2borderflow.core.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Составной ключ роли сотрудника оператора: пользователь + роль. */
@Embeddable
public class OperatorRoleId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    protected OperatorRoleId() {
    }

    public OperatorRoleId(UUID userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OperatorRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }
}
