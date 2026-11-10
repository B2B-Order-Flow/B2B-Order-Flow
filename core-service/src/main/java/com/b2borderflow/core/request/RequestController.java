package com.b2borderflow.core.request;

import com.b2borderflow.core.api.RequestsApi;
import com.b2borderflow.core.api.model.AssignManagerPayload;
import com.b2borderflow.core.api.model.AuditEntryPage;
import com.b2borderflow.core.api.model.CreateRequestPayload;
import com.b2borderflow.core.api.model.RequestPage;
import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.api.model.UpdateRequestPayload;
import com.b2borderflow.core.api.model.VersionedAction;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой заявок: контроллер только вызывает сервисы, логики здесь нет. */
@RestController
public class RequestController implements RequestsApi {

    private final RequestService requestService;
    private final RequestSubmissionService submissionService;

    public RequestController(RequestService requestService,
                             RequestSubmissionService submissionService) {
        this.requestService = requestService;
        this.submissionService = submissionService;
    }

    @Override
    public ResponseEntity<RequestView> createRequest(CreateRequestPayload createRequestPayload,
                                                     UUID idempotencyKey) {
        RequestView view = requestService.create(createRequestPayload, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/internal/v1/requests/" + view.getId()))
                .body(view);
    }

    @Override
    public ResponseEntity<RequestView> getRequest(UUID requestId) {
        return ResponseEntity.ok(requestService.get(requestId));
    }

    @Override
    public ResponseEntity<RequestView> updateDraft(UUID requestId, UpdateRequestPayload updateRequestPayload) {
        return ResponseEntity.ok(requestService.updateDraft(requestId, updateRequestPayload));
    }

    @Override
    public ResponseEntity<Void> deleteDraft(UUID requestId, Long version) {
        requestService.deleteDraft(requestId, version);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RequestView> submitRequest(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(submissionService.submit(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> recheckRequestData(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(submissionService.recheck(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> claimRequest(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(submissionService.claim(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> assignManager(UUID requestId, AssignManagerPayload assignManagerPayload) {
        return ResponseEntity.ok(submissionService.assignManager(
                requestId,
                assignManagerPayload.getManagerId(),
                assignManagerPayload.getVersion()));
    }

    @Override
    public ResponseEntity<RequestPage> listRequests(Integer page, Integer size, String sort,
                                                    com.b2borderflow.core.api.model.RequestStatus status,
                                                    UUID companyId, UUID managerId, Boolean unassigned) {
        RequestStatus domainStatus = status == null ? null : RequestStatus.valueOf(status.name());
        return ResponseEntity.ok(requestService.list(
                domainStatus, companyId, managerId, unassigned, page, size, sort));
    }

    @Override
    public ResponseEntity<AuditEntryPage> getRequestHistory(UUID requestId, Integer page, Integer size) {
        return ResponseEntity.ok(requestService.history(requestId, page, size));
    }
}
