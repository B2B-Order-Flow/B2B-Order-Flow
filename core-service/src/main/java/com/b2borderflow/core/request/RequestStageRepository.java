package com.b2borderflow.core.request;

import com.b2borderflow.core.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestStageRepository extends JpaRepository<RequestStage, UUID> {

    List<RequestStage> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    List<RequestStage> findByRequestIdInOrderByRequestIdAscCreatedAtAsc(Collection<UUID> requestIds);

    List<RequestStage> findByRequestIdAndRouteVersionOrderByCreatedAtAsc(UUID requestId, int routeVersion);

    List<RequestStage> findByAssigneeIdAndStatusInOrderByCreatedAtAsc(
            UUID assigneeId, Collection<RequestStageStatus> statuses);

    List<RequestStage> findByQueueRoleAndStatusOrderByCreatedAtAsc(Role queueRole, RequestStageStatus status);

    List<RequestStage> findByStatusOrderByCreatedAtAsc(RequestStageStatus status);

    Optional<RequestStage> findFirstByRequestIdAndTypeAndStatusInOrderByCreatedAtDesc(
            UUID requestId, RequestStageType type, Collection<RequestStageStatus> statuses);

    boolean existsByRequestIdAndAssigneeId(UUID requestId, UUID assigneeId);

    boolean existsByRequestIdAndQueueRoleAndStatus(UUID requestId, Role queueRole, RequestStageStatus status);

    boolean existsByRequestIdAndTypeAndStatus(UUID requestId, RequestStageType type, RequestStageStatus status);
}
