package com.b2borderflow.core.request;

import com.b2borderflow.core.api.WorkflowApi;
import com.b2borderflow.core.api.model.CommercialTermsPayload;
import com.b2borderflow.core.api.model.ContractConcludedPayload;
import com.b2borderflow.core.api.model.RequestView;
import com.b2borderflow.core.api.model.VersionedAction;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-слой переходов недели 2. Контроллер только вызывает сервис:
 * проверки роли, статуса и версии живут в домене.
 */
@RestController
public class WorkflowController implements WorkflowApi {

    private final RequestLifecycleService lifecycleService;

    public WorkflowController(RequestLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Override
    public ResponseEntity<RequestView> setCommercialTerms(UUID requestId,
                                                          CommercialTermsPayload commercialTermsPayload) {
        return ResponseEntity.ok(lifecycleService.setCommercialTerms(requestId, commercialTermsPayload));
    }

    @Override
    public ResponseEntity<RequestView> markContractSent(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(lifecycleService.markContractSent(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> concludeContract(UUID requestId,
                                                        ContractConcludedPayload contractConcludedPayload) {
        return ResponseEntity.ok(lifecycleService.concludeContract(requestId, contractConcludedPayload));
    }

    @Override
    public ResponseEntity<RequestView> acceptOffer(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(lifecycleService.acceptOffer(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> markActSent(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(lifecycleService.markActSent(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> signAct(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(lifecycleService.signAct(requestId, versionedAction.getVersion()));
    }

    @Override
    public ResponseEntity<RequestView> closeByDefault(UUID requestId, VersionedAction versionedAction) {
        return ResponseEntity.ok(lifecycleService.closeByDefault(requestId, versionedAction.getVersion()));
    }
}
