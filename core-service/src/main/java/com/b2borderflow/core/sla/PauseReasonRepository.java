package com.b2borderflow.core.sla;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PauseReasonRepository extends JpaRepository<PauseReason, PauseReasonCode> {

    List<PauseReason> findByActiveTrueOrderByCodeAsc();
}
