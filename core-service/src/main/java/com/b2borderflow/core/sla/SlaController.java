package com.b2borderflow.core.sla;

import com.b2borderflow.core.api.SlaApi;
import com.b2borderflow.core.api.model.SlaView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-слой SLA заявки. Пока реализован только {@code GET /requests/{id}/sla};
 * ручные паузы этапа ({@code pause}, {@code resume}, {@code extend-pause}) —
 * следующая часть недели (plan-week2.md, П2), поэтому {@link SlaApi} целиком
 * не реализуется: иначе пришлось бы отдавать заглушки на незапланированные
 * в этом PR операции.
 */
@RestController
public class SlaController {

    private final RequestSlaService slaService;

    public SlaController(RequestSlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping(SlaApi.PATH_GET_REQUEST_SLA)
    public ResponseEntity<SlaView> getRequestSla(@PathVariable("requestId") UUID requestId) {
        return ResponseEntity.ok(slaService.get(requestId));
    }
}
