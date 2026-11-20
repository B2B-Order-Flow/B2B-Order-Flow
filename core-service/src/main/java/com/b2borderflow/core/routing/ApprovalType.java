package com.b2borderflow.core.routing;

/**
 * Вид согласования, которое добавляет правило маршрутизации.
 * Профильные согласования идут одной параллельной волной, управленческое — второй
 * (docs/system/routing-rules.md, 6.2).
 */
public enum ApprovalType {

    LEGAL,
    FINANCE,
    PROCUREMENT,
    MANAGEMENT;

    /** Профильное согласование: попадает в первую волну. */
    public boolean isProfile() {
        return this != MANAGEMENT;
    }
}
