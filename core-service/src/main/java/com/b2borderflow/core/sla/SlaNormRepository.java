package com.b2borderflow.core.sla;

import com.b2borderflow.core.request.RequestStageType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaNormRepository extends JpaRepository<SlaNorm, UUID> {

    Optional<SlaNorm> findByProfileAndKindAndStageType(
            SlaProfile profile, SlaTimerKind kind, RequestStageType stageType);

    Optional<SlaNorm> findByProfileAndKindAndStageTypeIsNull(SlaProfile profile, SlaTimerKind kind);

    List<SlaNorm> findByProfileOrderByStageTypeAsc(SlaProfile profile);
}
