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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Неизменяемый снимок параметров заявки. Создаётся при отправке, доработке
 * и пересборке маршрута; снимок не редактируется.
 */
@Entity
@Table(name = "request_revision")
public class RequestRevision extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    private RevisionReason reason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> paramsSnapshot = new HashMap<>();

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

    public int getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(int revisionNo) {
        this.revisionNo = revisionNo;
    }

    public RevisionReason getReason() {
        return reason;
    }

    public void setReason(RevisionReason reason) {
        this.reason = reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Map<String, Object> getParamsSnapshot() {
        return paramsSnapshot;
    }

    public void setParamsSnapshot(Map<String, Object> paramsSnapshot) {
        this.paramsSnapshot = paramsSnapshot;
    }
}
