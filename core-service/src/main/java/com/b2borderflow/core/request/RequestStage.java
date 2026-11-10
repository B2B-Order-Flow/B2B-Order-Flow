package com.b2borderflow.core.request;

import com.b2borderflow.core.common.persistence.AuditableEntity;
import com.b2borderflow.core.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Этап маршрута заявки. В неделе 1 этапы ещё не создаются (они появляются на T04),
 * но сущность и схема готовы к следующей неделе.
 */
@Entity
@Table(name = "request_stage")
public class RequestStage extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "route_version", nullable = false)
    private int routeVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private RequestStageType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RequestStageStatus status = RequestStageStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_role", length = 32)
    private Role queueRole;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "needs_reassignment", nullable = false)
    private boolean needsReassignment;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_by_rules", nullable = false, columnDefinition = "text[]")
    private List<String> requiredByRules = new ArrayList<>();

    @Column(name = "taken_at")
    private OffsetDateTime takenAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

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

    public int getRouteVersion() {
        return routeVersion;
    }

    public void setRouteVersion(int routeVersion) {
        this.routeVersion = routeVersion;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public RequestStageType getType() {
        return type;
    }

    public void setType(RequestStageType type) {
        this.type = type;
    }

    public RequestStageStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStageStatus status) {
        this.status = status;
    }

    public Role getQueueRole() {
        return queueRole;
    }

    public void setQueueRole(Role queueRole) {
        this.queueRole = queueRole;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public boolean isNeedsReassignment() {
        return needsReassignment;
    }

    public void setNeedsReassignment(boolean needsReassignment) {
        this.needsReassignment = needsReassignment;
    }

    public List<String> getRequiredByRules() {
        return requiredByRules;
    }

    public void setRequiredByRules(List<String> requiredByRules) {
        this.requiredByRules = requiredByRules;
    }

    public OffsetDateTime getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(OffsetDateTime takenAt) {
        this.takenAt = takenAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
