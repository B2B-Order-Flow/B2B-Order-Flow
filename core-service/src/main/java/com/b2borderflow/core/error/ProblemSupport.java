package com.b2borderflow.core.error;

import com.b2borderflow.core.logging.CorrelationId;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Сборка тела ошибки RFC 9457 с полями контракта ядра. */
@Component
public class ProblemSupport {

    private static final String TYPE_BASE = "https://b2b-order-flow/errors/";

    public ProblemDetail create(ErrorCode code, String detail, String instance, List<Violation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                code.getStatus(),
                detail == null || detail.isBlank() ? code.getTitle() : detail);
        problem.setTitle(code.getTitle());
        problem.setType(URI.create(TYPE_BASE + code.getTypeSlug()));
        if (instance != null && !instance.isBlank()) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", CorrelationId.current());
        problem.setProperty("retryable", code.isRetryable());
        if (violations != null && !violations.isEmpty()) {
            problem.setProperty("violations", violations);
        }
        return problem;
    }
}
