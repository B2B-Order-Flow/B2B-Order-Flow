package com.b2borderflow.core.sla;

import com.b2borderflow.core.api.SlaApi;
import com.b2borderflow.core.api.model.SlaExtendPausePayload;
import com.b2borderflow.core.api.model.SlaPausePayload;
import com.b2borderflow.core.api.model.SlaView;
import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.api.model.VersionedAction;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-слой SLA: карточка SLA и ручные паузы этапа (api-contract.md, 6.8, 10.10).
 * Пути и сигнатуры берутся из сгенерированного {@link SlaApi}.
 */
@RestController
public class SlaController implements SlaApi {

    private final RequestSlaService slaService;
    private final StageSlaService stageSlaService;

    public SlaController(RequestSlaService slaService, StageSlaService stageSlaService) {
        this.slaService = slaService;
        this.stageSlaService = stageSlaService;
    }

    @Override
    public ResponseEntity<SlaView> getRequestSla(UUID requestId) {
        return ResponseEntity.ok(slaService.get(requestId));
    }

    @Override
    public ResponseEntity<StageView> pauseStageSla(UUID stageId, SlaPausePayload payload) {
        return ResponseEntity.ok(stageSlaService.pause(stageId, payload));
    }

    @Override
    public ResponseEntity<StageView> resumeStageSla(UUID stageId, VersionedAction action) {
        return ResponseEntity.ok(stageSlaService.resume(stageId, action.getVersion()));
    }

    @Override
    public ResponseEntity<StageView> extendStageSlaPause(UUID stageId, SlaExtendPausePayload payload) {
        return ResponseEntity.ok(stageSlaService.extend(stageId, payload));
    }
}
