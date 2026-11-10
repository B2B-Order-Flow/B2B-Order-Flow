package com.b2borderflow.core.company;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRoleRepository extends JpaRepository<MembershipRole, MembershipRoleId> {

    List<MembershipRole> findByIdMembershipId(UUID membershipId);

    List<MembershipRole> findByIdMembershipIdIn(Collection<UUID> membershipIds);
}
