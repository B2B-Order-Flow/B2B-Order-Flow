package com.b2borderflow.core.request;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRevisionRepository extends JpaRepository<RequestRevision, UUID> {

    Optional<RequestRevision> findTopByRequestIdOrderByRevisionNoDesc(UUID requestId);
}
