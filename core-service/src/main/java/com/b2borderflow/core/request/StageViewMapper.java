package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.StageView;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.user.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Представление этапа для очереди и карточки заявки (docs/system/api-contract.md, 6.4). */
@Component
public class StageViewMapper {

    private final AppUserRepository userRepository;

    public StageViewMapper(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public StageView toView(RequestStage stage, String requestNumber, boolean clientView) {
        StageView view = new StageView()
                .id(stage.getId())
                .requestId(stage.getRequestId())
                .requestNumber(requestNumber)
                .type(ApiEnums.stageType(stage.getType()))
                .status(ApiEnums.stageStatus(stage.getStatus()))
                .routeVersion(stage.getRouteVersion())
                .version(stage.getVersion())
                .createdAt(stage.getCreatedAt())
                .takenAt(stage.getTakenAt())
                .finishedAt(stage.getFinishedAt());

        if (clientView) {
            // Клиенту — только тип и статус этапа; исполнители и служебные поля скрыты.
            view.assigneeId(null)
                    .assigneeName(null)
                    .needsReassignment(false)
                    .queueRole(null)
                    .requiredByRules(List.of());
        } else {
            view.assigneeId(stage.getAssigneeId())
                    .assigneeName(assigneeName(stage.getAssigneeId()))
                    .needsReassignment(stage.isNeedsReassignment())
                    .queueRole(ApiEnums.role(stage.getQueueRole()))
                    .requiredByRules(stage.getRequiredByRules() == null
                            ? List.of()
                            : List.copyOf(stage.getRequiredByRules()));
        }
        return view;
    }

    private String assigneeName(java.util.UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .map(user -> user.getFullName())
                .orElse(null);
    }
}
