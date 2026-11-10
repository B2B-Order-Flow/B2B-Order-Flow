package com.b2borderflow.core.request;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalCheckRepository extends JpaRepository<ExternalCheck, UUID> {

    List<ExternalCheck> findByRequestIdOrderByCheckedAtAsc(UUID requestId);

    @Query("select count(distinct ec.system) from ExternalCheck ec "
            + "where ec.requestId = :requestId and ec.status = com.b2borderflow.core.request.ExternalCheckStatus.OK")
    long countSuccessfulSystems(@Param("requestId") UUID requestId);

    @Query("select ec from ExternalCheck ec "
            + "where ec.requestId = :requestId and ec.status = com.b2borderflow.core.request.ExternalCheckStatus.OK "
            + "order by ec.checkedAt desc")
    List<ExternalCheck> findSuccessful(@Param("requestId") UUID requestId);
}
