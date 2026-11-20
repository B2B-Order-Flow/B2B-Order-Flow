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

    public SlaProfile getProfile() {
        return profile;
    }

    public void setProfile(SlaProfile profile) {
        this.profile = profile == null ? SlaProfile.PROD : profile;
    }
}
