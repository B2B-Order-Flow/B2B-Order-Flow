package com.b2borderflow.core.request;

/** Статусы этапа (docs/system/status-model.md, 4.2). */
public enum RequestStageStatus {
    QUEUED,
    IN_PROGRESS,
    DONE,
    RETURNED,
    REJECTED,
    SUPERSEDED,
    CANCELLED
}
