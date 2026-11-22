package com.b2borderflow.core.config;

import com.b2borderflow.core.sla.SlaProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Профиль расчёта SLA (docs/system/sla.md, 3.1–3.2). Задаётся конфигурацией
 * {@code app.sla.profile} (переменная окружения {@code SLA_PROFILE}) на уровне
 * системы, а не отдельной заявки.
 */
@Component
@ConfigurationProperties(prefix = "app.sla")
public class SlaProperties {

    private SlaProfile profile = SlaProfile.PROD;

    /** Пауза между запусками планировщика. */
    private long schedulerDelayMs = 60_000;

    /** Сколько таймеров обрабатывать за один проход. */
    private int schedulerBatchSize = 100;

    public SlaProfile getProfile() {
        return profile;
    }

    public void setProfile(SlaProfile profile) {
        this.profile = profile == null ? SlaProfile.PROD : profile;
    }

    public long getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(long schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public int getSchedulerBatchSize() {
        return schedulerBatchSize;
    }

    public void setSchedulerBatchSize(int schedulerBatchSize) {
        this.schedulerBatchSize = schedulerBatchSize;
    }
}
