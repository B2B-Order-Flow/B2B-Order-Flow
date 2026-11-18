package com.b2borderflow.core.sla;

import com.b2borderflow.core.common.persistence.CreatedEntity;
import com.b2borderflow.core.request.RequestStageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Норматив SLA из справочника {@code sla_norm} (docs/system/sla.md, раздел 4).
 * Норматив задаётся на этап ({@code kind = STAGE_SLA}) или на контрольный срок заявки;
 * {@code stage_type} заполнен только для этапа.
 */
@Entity
@Table(name = "sla_norm")
public class SlaNorm extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile", nullable = false, length = 8)
    private SlaProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private SlaTimerKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", length = 32)
    private RequestStageType stageType;

    @Column(name = "norm_minutes", nullable = false)
    private int normMinutes;

    @Column(name = "warn_before_minutes", nullable = false)
    private int warnBeforeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "calendar_kind", nullable = false, length = 16)
    private SlaCalendarKind calendarKind;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SlaProfile getProfile() {
        return profile;
    }

    public void setProfile(SlaProfile profile) {
        this.profile = profile;
    }

    public SlaTimerKind getKind() {
        return kind;
    }

    public void setKind(SlaTimerKind kind) {
        this.kind = kind;
    }

    public RequestStageType getStageType() {
        return stageType;
    }

    public void setStageType(RequestStageType stageType) {
        this.stageType = stageType;
    }

    public int getNormMinutes() {
        return normMinutes;
    }

    public void setNormMinutes(int normMinutes) {
        this.normMinutes = normMinutes;
    }

    public int getWarnBeforeMinutes() {
        return warnBeforeMinutes;
    }

    public void setWarnBeforeMinutes(int warnBeforeMinutes) {
        this.warnBeforeMinutes = warnBeforeMinutes;
    }

    public SlaCalendarKind getCalendarKind() {
        return calendarKind;
    }

    public void setCalendarKind(SlaCalendarKind calendarKind) {
        this.calendarKind = calendarKind;
    }
}
