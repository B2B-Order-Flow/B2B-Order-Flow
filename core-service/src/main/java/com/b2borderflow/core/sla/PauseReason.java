package com.b2borderflow.core.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Причина паузы из справочника {@code pause_reason} (docs/system/sla.md, 6.1):
 * автоматическая ли, каким ролям разрешено ставить и снимать её вручную.
 */
@Entity
@Table(name = "pause_reason")
public class PauseReason {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 64)
    private PauseReasonCode code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "setter_roles", nullable = false, columnDefinition = "text[]")
    private List<String> setterRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "remover_roles", nullable = false, columnDefinition = "text[]")
    private List<String> removerRoles = new ArrayList<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public PauseReasonCode getCode() {
        return code;
    }

    public void setCode(PauseReasonCode code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public void setAutomatic(boolean automatic) {
        this.automatic = automatic;
    }

    public List<String> getSetterRoles() {
        return setterRoles;
    }

    public void setSetterRoles(List<String> setterRoles) {
        this.setterRoles = setterRoles;
    }

    public List<String> getRemoverRoles() {
        return removerRoles;
    }

    public void setRemoverRoles(List<String> removerRoles) {
        this.removerRoles = removerRoles;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
