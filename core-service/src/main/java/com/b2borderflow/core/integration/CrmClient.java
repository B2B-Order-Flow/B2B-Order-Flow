package com.b2borderflow.core.integration;

import com.b2borderflow.core.request.ExternalSystem;
import com.b2borderflow.core.request.Segment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Клиент mock CRM: сегмент клиента и закреплённый менеджер (integrations.md, 4.1). */
@Component
public class CrmClient {

    private static final String PATH = "/crm/v1/customers";
    private static final String URI_TEMPLATE = PATH + "?inn={inn}";

    private final RestClient restClient;
    private final ExternalCallExecutor executor;
    private final MockHttpClient httpClient;

    public CrmClient(@Qualifier("crmRestClient") RestClient restClient,
                     ExternalCallExecutor executor,
                     MockHttpClient httpClient) {
        this.restClient = restClient;
        this.executor = executor;
        this.httpClient = httpClient;
    }

    public ExternalCallResult<CrmData> fetch(String inn) {
        ExternalCallResult<ExternalRawResponse> raw = executor.execute(
                ExternalSystem.CRM,
                PATH,
                () -> httpClient.get(restClient, URI_TEMPLATE, inn));

        return raw.mapData(ignored -> {
            if (raw.notFound()) {
                return new CrmData(null, null);
            }
            return new CrmData(
                    parseSegment(ExternalPayload.string(raw.rawPayload(), "segment")),
                    ExternalPayload.string(raw.rawPayload(), "assignedManagerLogin"));
        });
    }

    private static Segment parseSegment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Segment.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Ответ CRM в объёме, который нужен ядру. */
    public record CrmData(Segment segment, String assignedManagerLogin) {
    }
}
