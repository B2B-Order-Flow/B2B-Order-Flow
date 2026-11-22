package com.b2borderflow.core.sla;

/** Состояние таймера SLA (docs/system/sla.md, 5.1). */
public enum SlaTimerStatus {

    RUNNING,
    PAUSED,
    STOPPED;

    public boolean isActive() {
        return this != STOPPED;
    }
}
