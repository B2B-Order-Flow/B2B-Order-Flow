package com.b2borderflow.core.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Порог маршрутизации из справочника {@code routing_config}
 * (docs/system/routing-rules.md, раздел 4). Значение хранится в JSONB, потому что
 * пороги бывают и числом, и массивом сегментов; меняет их администратор, изменение
 * пишется в аудит.
 */
@Entity
@Table(name = "routing_config")
public class RoutingConfig {

    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    /**
     * Разобранное значение JSONB: число, строка, список или объект. Хранится как
     * {@code Object}, а не как {@code JsonNode}, чтобы не зависеть от того, какой
     * Jackson (2 или 3) выбрала среда Hibernate.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value", nullable = false, columnDefinition = "jsonb")
    private Object configValue;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public Object getConfigValue() {
        return configValue;
    }

    public void setConfigValue(Object configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
