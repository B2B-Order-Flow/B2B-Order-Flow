package com.b2borderflow.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Настройки безопасности ядра. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** HMAC-секрет JWT. Не попадает в репозиторий и логи. */
    @NotBlank
    @Size(min = 32, message = "JWT_SECRET должен быть не короче 32 символов")
    private String jwtSecret;

    @NotNull
    private Duration jwtTtl = Duration.ofHours(8);

    /** Общий секрет BFF → ядро в заголовке X-Internal-Token. */
    @NotBlank
    private String internalToken;

    private String issuer = "core-service";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getJwtTtl() {
        return jwtTtl;
    }

    public void setJwtTtl(Duration jwtTtl) {
        this.jwtTtl = jwtTtl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
