package com.b2borderflow.core.sla;

import com.b2borderflow.core.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Таймер срока (docs/system/domain-model.md, 4.4).
 *
 * <p>{@code stageId} заполнен только для {@code kind = STAGE_SLA}; контрольные сроки
 * заявки привязаны к ней одной. {@code due_at} — момент нарушения при условии, что
 * пауз больше не будет; {@code next_event_at} индексируется и по нему выбирает
 * планировщик (docs/system/sla.md, 9).</p>
 */
@Entity
@Table(name = "sla_timer")
public class SlaTimer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "stage_id", updatable = false)
    private UUID stageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 32)
    private SlaTimerKind kind;

    @Column(name = "norm_minutes", nullable = false, updatable = false)
    private int normMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SlaTimerStatus status = SlaTimerStatus.RUNNING;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "warn_at")
    private OffsetDateTime warnAt;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "escalate_at")
    private OffsetDateTime escalateAt;

    @Column(name = "elapsed_sla_seconds", nullable = false)
    private long elapsedSlaSeconds;

    @Column(name = "next_event_at")
    private OffsetDateTime nextEventAt;

    @Column(name = "warned_at")
    private OffsetDateTime warnedAt;

    @Column(name = "breached_at")
    private OffsetDateTime breachedAt;

    @Column(name = "escalation_level", nullable = false)
    private short escalationLevel;

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

    public UUID getStageId() {
        return stageId;
    }

    public void setStageId(UUID stageId) {
        this.stageId = stageId;
    }

    public SlaTimerKind getKind() {
        return kind;
    }

    public void setKind(SlaTimerKind kind) {
        this.kind = kind;
    }

    public int getNormMinutes() {
        return normMinutes;
    }

    public void setNormMinutes(int normMinutes) {
        this.normMinutes = normMinutes;
    }

    public SlaTimerStatus getStatus() {
        return status;
    }

    public void setStatus(SlaTimerStatus status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getWarnAt() {
        return warnAt;
    }

    public void setWarnAt(OffsetDateTime warnAt) {
        this.warnAt = warnAt;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public OffsetDateTime getEscalateAt() {
        return escalateAt;
    }

    public void setEscalateAt(OffsetDateTime escalateAt) {
        this.escalateAt = escalateAt;
    }

    public long getElapsedSlaSeconds() {
        return elapsedSlaSeconds;
    }

    public void setElapsedSlaSeconds(long elapsedSlaSeconds) {
        this.elapsedSlaSeconds = elapsedSlaSeconds;
    }

    public OffsetDateTime getNextEventAt() {
        return nextEventAt;
    }

    public void setNextEventAt(OffsetDateTime nextEventAt) {
        this.nextEventAt = nextEventAt;
    }

    public OffsetDateTime getWarnedAt() {
        return warnedAt;
    }

    public void setWarnedAt(OffsetDateTime warnedAt) {
        this.warnedAt = warnedAt;
    }

    public OffsetDateTime getBreachedAt() {
        return breachedAt;
    }

    public void setBreachedAt(OffsetDateTime breachedAt) {
        this.breachedAt = breachedAt;
    }

    public short getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(short escalationLevel) {
        this.escalationLevel = escalationLevel;
    }
}
