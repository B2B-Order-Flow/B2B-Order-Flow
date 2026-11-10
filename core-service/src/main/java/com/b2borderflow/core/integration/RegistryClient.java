package com.b2borderflow.core.integration;

import com.b2borderflow.core.request.ExternalSystem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Клиент mock справочника клиентов: реквизиты и признак госзаказчика (integrations.md, 4.3). */
@Component
public class RegistryClient {

    private static final String PATH = "/registry/v1/organizations";
    private static final String URI_TEMPLATE = PATH + "?inn={inn}";

    private final RestClient restClient;
    private final ExternalCallExecutor executor;
    private final MockHttpClient httpClient;

    public RegistryClient(@Qualifier("registryRestClient") RestClient restClient,
                          ExternalCallExecutor executor,
                          MockHttpClient httpClient) {
        this.restClient = restClient;
        this.executor = executor;
        this.httpClient = httpClient;
    }

    public ExternalCallResult<RegistryData> fetch(String inn) {
        ExternalCallResult<ExternalRawResponse> raw = executor.execute(
                ExternalSystem.REGISTRY,
                PATH,
                () -> httpClient.get(restClient, URI_TEMPLATE, inn));

        return raw.mapData(ignored -> {
            if (raw.notFound()) {
                return new RegistryData(false, false, null, null, null, null, null);
            }
            return new RegistryData(
                    true,
                    ExternalPayload.bool(raw.rawPayload(), "isGovCustomer"),
                    ExternalPayload.string(raw.rawPayload(), "status"),
                    ExternalPayload.string(raw.rawPayload(), "kpp"),
                    ExternalPayload.string(raw.rawPayload(), "ogrn"),
                    ExternalPayload.string(raw.rawPayload(), "name"),
                    ExternalPayload.string(raw.rawPayload(), "legalAddress"));
        });
    }

    public record RegistryData(boolean found, boolean govCustomer, String status,
                               String kpp, String ogrn, String name, String legalAddress) {
    }
}
