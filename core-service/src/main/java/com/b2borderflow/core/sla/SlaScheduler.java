package com.b2borderflow.core.sla;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодический запуск SLA Monitor (docs/system/sla.md, 9).
 * В интеграционных тестах отключается свойством {@code app.sla.scheduler-enabled=false}:
 * тест сам вызывает {@link SlaMonitor#processDueTimers()} на управляемом времени.
 */
@Component
@ConditionalOnProperty(name = "app.sla.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SlaScheduler {

    private final SlaMonitor monitor;

    public SlaScheduler(SlaMonitor monitor) {
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${app.sla.scheduler-delay-ms:60000}")
    public void run() {
        monitor.processDueTimers();
    }
}
