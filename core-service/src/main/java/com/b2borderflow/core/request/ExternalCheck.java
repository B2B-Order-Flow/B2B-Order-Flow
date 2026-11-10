package com.b2borderflow.core.request;

import com.b2borderflow.core.common.persistence.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Результат запроса к mock-системе. Сырой ответ хранится целиком (JSONB),
 * по нему не ищем; числа попыток и статус — обычные колонки.
 */
@Entity
@Table(name = "external_check")
public class ExternalCheck extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false, length = 16)
    private ExternalSystem system;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 8)
    private ExternalCheckStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new HashMap<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public ExternalSystem getSystem() {
        return system;
    }

    public void setSystem(ExternalSystem system) {
        this.system = system;
    }

    public ExternalCheckStatus getStatus() {
        return status;
    }

    public void setStatus(ExternalCheckStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(OffsetDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
