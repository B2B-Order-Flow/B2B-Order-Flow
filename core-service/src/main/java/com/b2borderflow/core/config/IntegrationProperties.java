package com.b2borderflow.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Адреса и политика повторов вызовов mock-сервисов (docs/system/integrations.md, 6). */
@ConfigurationProperties(prefix = "app.integration")
public class IntegrationProperties {

    private String crmUrl = "http://localhost:8081";
    private String billingUrl = "http://localhost:8082";
    private String registryUrl = "http://localhost:8083";

    /** Таймаут соединения; меньше общего таймаута попытки. */
    private Duration connectTimeout = Duration.ofSeconds(1);

    /** Таймаут одной попытки вместе с ответом: 2 с. */
    private Duration readTimeout = Duration.ofSeconds(2);

    private int maxAttempts = 3;
    private Duration retryDelay = Duration.ofMillis(200);
    private double retryMultiplier = 3.0;
    private Duration retryMaxDelay = Duration.ofSeconds(2);

    public String getCrmUrl() {
        return crmUrl;
    }

    public void setCrmUrl(String crmUrl) {
        this.crmUrl = crmUrl;
    }

    public String getBillingUrl() {
        return billingUrl;
    }

    public void setBillingUrl(String billingUrl) {
        this.billingUrl = billingUrl;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public Duration getRetryMaxDelay() {
        return retryMaxDelay;
    }

    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = retryMaxDelay;
    }
}
