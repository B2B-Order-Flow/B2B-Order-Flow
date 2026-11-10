package com.b2borderflow.core.integration;

import com.b2borderflow.core.request.ExternalSystem;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Клиент mock биллинга: задолженность клиента (integrations.md, 4.2). */
@Component
public class BillingClient {

    private static final String PATH = "/billing/v1/accounts/{inn}/balance";

    private final RestClient restClient;
    private final ExternalCallExecutor executor;
    private final MockHttpClient httpClient;

    public BillingClient(@Qualifier("billingRestClient") RestClient restClient,
                         ExternalCallExecutor executor,
                         MockHttpClient httpClient) {
        this.restClient = restClient;
        this.executor = executor;
        this.httpClient = httpClient;
    }

    public ExternalCallResult<BillingData> fetch(String inn) {
        ExternalCallResult<ExternalRawResponse> raw = executor.execute(
                ExternalSystem.BILLING,
                "/billing/v1/accounts/{inn}/balance",
                () -> httpClient.get(restClient, PATH, inn));

        return raw.mapData(ignored -> {
            if (raw.notFound()) {
                // Лицевого счёта нет — новый клиент, долг 0.
                return new BillingData(BigDecimal.ZERO);
            }
            return new BillingData(ExternalPayload.decimal(raw.rawPayload(), "debtAmount", BigDecimal.ZERO));
        });
    }

    public record BillingData(BigDecimal debtAmount) {
    }
}
