package com.b2borderflow.core.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Причина отклонения из справочника {@code rejection_reason}
 * (docs/system/api-contract.md, 6.13 и 10.8). На неделе 2 заполнен только
 * {@code OTHER}; проверка существования кода защищает внешний ключ заявки.
 */
@Entity
@Table(name = "rejection_reason")
public class RejectionReason {

    @Id
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "stage_type", length = 32)
    private String stageType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStageType() {
        return stageType;
    }

    public void setStageType(String stageType) {
        this.stageType = stageType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
