package com.b2borderflow.core.request;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RequestRepository extends JpaRepository<Request, UUID>, JpaSpecificationExecutor<Request> {

    Optional<Request> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByNumber(String number);
}
