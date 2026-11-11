package com.b2borderflow.core.company;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

    List<CompanyMembership> findByUserIdAndRemovedAtIsNullOrderByAddedAtAsc(UUID userId);

    Optional<CompanyMembership> findFirstByUserIdAndCompanyIdAndRemovedAtIsNull(UUID userId, UUID companyId);

    List<CompanyMembership> findByCompanyIdInAndRemovedAtIsNull(Collection<UUID> companyIds);

    long countByUserIdAndRemovedAtIsNull(UUID userId);
}
