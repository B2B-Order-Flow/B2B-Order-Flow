package com.b2borderflow.core.company;

import com.b2borderflow.core.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRoleRepository extends JpaRepository<OperatorRole, OperatorRoleId> {

    List<OperatorRole> findByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdRole(UUID userId, Role role);
}
