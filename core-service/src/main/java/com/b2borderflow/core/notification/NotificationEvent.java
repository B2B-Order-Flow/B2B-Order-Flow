package com.b2borderflow.core.notification;

/** Коды событий уведомлений (docs/system/api-contract.md, раздел 7). */
public final class NotificationEvent {

    public static final String REQUEST_ASSIGNED = "REQUEST_ASSIGNED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String TECH_RESULT_NONE = "TECH_RESULT_NONE";
    public static final String CONTRACT_READY = "CONTRACT_READY";
    public static final String SERVICE_ACTIVATED = "SERVICE_ACTIVATED";
    public static final String ACT_SENT = "ACT_SENT";
    public static final String REQUEST_CLOSED = "REQUEST_CLOSED";
    public static final String STAGE_QUEUED = "STAGE_QUEUED";
    public static final String STAGE_ASSIGNED = "STAGE_ASSIGNED";

    private NotificationEvent() {
    }
}
