package com.b2borderflow.core.request;

import com.b2borderflow.core.api.StagesApi;
import com.b2borderflow.core.api.model.Role;
import com.b2borderflow.core.api.model.StageAssignPayload;
import com.b2borderflow.core.api.model.StagePage;
import com.b2borderflow.core.api.model.StageStatus;
import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.api.model.TechResultPayload;
import com.b2borderflow.core.api.model.VersionedAction;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой этапов: очередь, «мои задачи», команды этапа. */
@RestController
public class StagesController implements StagesApi {

    private final StageService stageService;

    public StagesController(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    public ResponseEntity<StagePage> listStages(Role queueRole, StageStatus status,
                                                Integer page, Integer size, String sort) {
        return ResponseEntity.ok(stageService.list(queueRole, status, page, size, sort));
    }

    @Override
    public ResponseEntity<StagePage> listMyStages(Integer page, Integer size) {
        return ResponseEntity.ok(stageService.my(page, size));
    }

    @Override
    public ResponseEntity<StageView> claimStage(UUID stageId, VersionedAction versionedAction) {
        return ResponseEntity.ok(stageService.claim(stageId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<StageView> assignStage(UUID stageId, StageAssignPayload stageAssignPayload) {
        return ResponseEntity.ok(stageService.assign(stageId, stageAssignPayload));
    }

    @Override
    public ResponseEntity<StageView> setTechResult(UUID stageId, TechResultPayload techResultPayload) {
        return ResponseEntity.ok(stageService.techResult(stageId, techResultPayload));
    }

    @Override
    public ResponseEntity<StageView> completeStage(UUID stageId, VersionedAction versionedAction) {
        return ResponseEntity.ok(stageService.complete(stageId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<StageView> activateStage(UUID stageId, VersionedAction versionedAction) {
        return ResponseEntity.ok(stageService.activate(stageId, versionedAction.getVersion()));
    }
}
