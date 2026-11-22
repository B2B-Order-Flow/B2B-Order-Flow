package com.b2borderflow.core.sla;

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
import java.util.UUID;

/**
 * Запись журнала паузы SLA (docs/system/domain-model.md, 4.4).
 * На одном таймере одновременно активна одна пауза; причина — только из
 * закрытого справочника, ручная пауза требует комментария (docs/system/sla.md, 6.2).
 */
@Entity
@Table(name = "sla_pause")
public class SlaPause extends CreatedEntity {

    public static final String SET_BY_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timer_id", nullable = false, updatable = false)
    private UUID timerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 64)
    private PauseReasonCode reasonCode;

    /** Пользователь или {@link #SET_BY_SYSTEM}; в БД это text, а не внешний ключ. */
    @Column(name = "set_by", nullable = false, updatable = false)
    private String setBy;

    @Column(name = "comment", updatable = false)
    private String comment;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "limit_at", nullable = false)
    private OffsetDateTime limitAt;

    @Column(name = "extended_count", nullable = false)
    private int extendedCount;

    @Column(name = "extended_by")
    private UUID extendedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTimerId() {
        return timerId;
    }

    public void setTimerId(UUID timerId) {
        this.timerId = timerId;
    }

    public PauseReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(PauseReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getSetBy() {
        return setBy;
    }

    public void setSetBy(String setBy) {
        this.setBy = setBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public OffsetDateTime getLimitAt() {
        return limitAt;
    }

    public void setLimitAt(OffsetDateTime limitAt) {
        this.limitAt = limitAt;
    }

    public int getExtendedCount() {
        return extendedCount;
    }

    public void setExtendedCount(int extendedCount) {
        this.extendedCount = extendedCount;
    }

    public UUID getExtendedBy() {
        return extendedBy;
    }

    public void setExtendedBy(UUID extendedBy) {
        this.extendedBy = extendedBy;
    }
}
