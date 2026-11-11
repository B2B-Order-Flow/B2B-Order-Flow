package com.b2borderflow.core.company;

import com.b2borderflow.core.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Составной ключ роли в компании: членство + роль. */
@Embeddable
public class MembershipRoleId implements Serializable {

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    protected MembershipRoleId() {
    }

    public MembershipRoleId(UUID membershipId, Role role) {
        this.membershipId = membershipId;
        this.role = role;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MembershipRoleId that)) {
            return false;
        }
        return Objects.equals(membershipId, that.membershipId) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId, role);
    }
}
