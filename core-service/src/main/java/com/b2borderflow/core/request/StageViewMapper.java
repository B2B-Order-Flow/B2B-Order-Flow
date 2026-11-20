package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.api.support.ApiEnums;
import java.util.List;
import org.springframework.stereotype.Component;

/** Представление этапа для очереди и карточки заявки (docs/system/api-contract.md, 6.4). */
@Component
public class StageViewMapper {

    public StageView toView(RequestStage stage, String requestNumber, boolean clientView) {
        StageView view = new StageView()
                .id(stage.getId())
                .requestId(stage.getRequestId())
                .requestNumber(requestNumber)
                .type(ApiEnums.stageType(stage.getType()))
                .status(ApiEnums.stageStatus(stage.getStatus()))
                .queueRole(ApiEnums.role(stage.getQueueRole()))
                .requiredByRules(stage.getRequiredByRules() == null
                        ? List.of()
                        : List.copyOf(stage.getRequiredByRules()))
                .routeVersion(stage.getRouteVersion())
                .version(stage.getVersion())
                .createdAt(stage.getCreatedAt())
                .takenAt(stage.getTakenAt())
                .finishedAt(stage.getFinishedAt());

        if (clientView) {
            // Клиенту не отдаются исполнители и служебное состояние переназначения.
            view.assigneeId(null).needsReassignment(false);
        } else {
            view.assigneeId(stage.getAssigneeId())
                    .needsReassignment(stage.isNeedsReassignment());
        }
        return view;
    }
}
