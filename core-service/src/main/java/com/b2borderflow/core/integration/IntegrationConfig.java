package com.b2borderflow.core.integration;

import com.b2borderflow.core.config.IntegrationProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP-клиенты mock-сервисов и пул для параллельных внешних вызовов. */
@Configuration
public class IntegrationConfig {

    @Bean
    public RestClient crmRestClient(IntegrationProperties properties) {
        return restClient(properties.getCrmUrl(), properties);
    }

    @Bean
    public RestClient billingRestClient(IntegrationProperties properties) {
        return restClient(properties.getBillingUrl(), properties);
    }

    @Bean
    public RestClient registryRestClient(IntegrationProperties properties) {
        return restClient(properties.getRegistryUrl(), properties);
    }

    /**
     * Виртуальные потоки: три внешних вызова на заявку не занимают платформенные
     * потоки и не упираются в размер пула при параллельных отправках.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService externalTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private static RestClient restClient(String baseUrl, IntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
