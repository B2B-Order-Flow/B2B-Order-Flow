package com.b2borderflow.core.request;

import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.integration.ExternalCallResult;
import com.b2borderflow.core.integration.ExternalCheckService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Оркестратор отправки заявки: короткая транзакция T01, затем внешние вызовы
 * вне транзакции, затем транзакция результатов (status-model.md, 6.1.1).
 * Соединение с БД на время сетевых вызовов не удерживается.
 */
@Service
public class RequestSubmissionService {

    private final RequestWorkflowService workflowService;
    private final ExternalCheckService externalCheckService;

    public RequestSubmissionService(RequestWorkflowService workflowService,
                                    ExternalCheckService externalCheckService) {
        this.workflowService = workflowService;
        this.externalCheckService = externalCheckService;
    }

    public RequestView submit(UUID requestId, long expectedVersion) {
        RequestWorkflowService.SubmissionStart start =
                workflowService.startSubmission(requestId, expectedVersion);

        Map<ExternalSystem, ExternalCallResult<?>> results =
                externalCheckService.checkAll(start.companyInn());

        return workflowService.completeSubmission(start.requestId(), results, false);
    }

    public RequestView recheck(UUID requestId, long expectedVersion) {
        RequestWorkflowService.RecheckStart start = workflowService.startRecheck(requestId, expectedVersion);

        Map<ExternalSystem, ExternalCallResult<?>> results =
                externalCheckService.checkMissing(start.companyInn(), start.successfulSystems());

        return workflowService.completeSubmission(start.requestId(), results, true);
    }

    public RequestView claim(UUID requestId, long expectedVersion) {
        return workflowService.claim(requestId, expectedVersion);
    }

    public RequestView assignManager(UUID requestId, UUID managerId, long expectedVersion) {
        return workflowService.assignManager(requestId, managerId, expectedVersion);
    }
}
