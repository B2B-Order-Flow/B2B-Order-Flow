package com.b2borderflow.core.sla;

/**
 * Закрытый справочник причин паузы (docs/system/sla.md, 6.1).
 * Вручную координатор ставит только {@link #AWAIT_CLIENT_DATA}, {@link #NO_ACCESS}
 * и {@link #CLIENT_RESCHEDULED}; остальные причины системные.
 */
public enum PauseReasonCode {

    REWORK,
    AWAIT_CLIENT_SIGNATURE,
    AWAIT_CLIENT_DATA,
    NO_ACCESS,
    CLIENT_RESCHEDULED,
    CANCEL_REVIEW,
    RECHECK_TECH;

    public boolean isManualByCoordinator() {
        return this == AWAIT_CLIENT_DATA
                || this == NO_ACCESS
                || this == CLIENT_RESCHEDULED;
    }
}
