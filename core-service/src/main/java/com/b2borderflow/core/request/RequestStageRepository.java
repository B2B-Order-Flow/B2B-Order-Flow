package com.b2borderflow.core.request;

import com.b2borderflow.core.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestStageRepository extends JpaRepository<RequestStage, UUID> {

    List<RequestStage> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    boolean existsByRequestIdAndAssigneeId(UUID requestId, UUID assigneeId);

    boolean existsByRequestIdAndQueueRoleAndStatus(UUID requestId, Role queueRole, RequestStageStatus status);
}
