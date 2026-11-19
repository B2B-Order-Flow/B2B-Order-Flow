package com.b2borderflow.core.request;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectionReasonRepository extends JpaRepository<RejectionReason, String> {

    Optional<RejectionReason> findByCodeAndActiveTrue(String code);
}
