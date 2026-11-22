package com.b2borderflow.core.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.config.SlaProperties;
import com.b2borderflow.core.notification.NotificationService;
import com.b2borderflow.core.request.RequestRepository;
import com.b2borderflow.core.request.RequestStageRepository;
import com.b2borderflow.core.support.WeekTwoApiSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Планировщик SLA на фиксированном времени: предупреждение, нарушение,
 * эскалация, контрольный срок и предел паузы (docs/system/sla.md, 5–9).
 */
class SlaMonitorIntegrationTest extends WeekTwoApiSupport {

    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(
            Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private SlaTimerRepository timerRepository;

    @Autowired
    private SlaPauseRepository pauseRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private RequestStageRepository stageRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SlaProperties slaProperties;

    @Test
    void stageTimerGoesThroughWarningBreachAndEscalationIdempotently() {
        Flow flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        UUID timerId = timerId(stageId);

        updateTimer(timerId, NOW.minusMinutes(1), NOW.plusHours(1), NOW.plusHours(2), NOW.minusMinutes(1));

        assertThat(monitor().processDueTimers()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select warned_at is not null from sla_timer where id = ?", Boolean.class, timerId)).isTrue();
        assertThat(notificationCount(flow, "SLA_WARNING")).isPositive();
        assertThat(auditCount(flow, "SLA_WARNING_SENT")).isEqualTo(1);

        int warningsAfterFirstPass = notificationCount(flow, "SLA_WARNING");
        assertThat(monitor().processDueTimers()).isGreaterThanOrEqualTo(0);
        assertThat(notificationCount(flow, "SLA_WARNING")).isEqualTo(warningsAfterFirstPass);

        updateTimer(timerId, NOW.minusMinutes(1), NOW.minusMinutes(1), NOW.plusHours(2), NOW.minusMinutes(1));
        monitor().processDueTimers();
        assertThat(jdbc.queryForObject(
                "select breached_at is not null from sla_timer where id = ?", Boolean.class, timerId)).isTrue();
        assertThat(jdbc.queryForObject(
                "select escalation_level from sla_timer where id = ?", Integer.class, timerId)).isEqualTo(1);
        assertThat(notificationCount(flow, "SLA_BREACH")).isPositive();
        assertThat(auditCount(flow, "SLA_BREACH_RECORDED")).isEqualTo(1);

        updateTimer(timerId, NOW.minusMinutes(1), NOW.minusMinutes(1), NOW.minusMinutes(1), NOW.minusMinutes(1));
        monitor().processDueTimers();
        assertThat(jdbc.queryForObject(
                "select escalation_level from sla_timer where id = ?", Integer.class, timerId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select next_event_at is null from sla_timer where id = ?", Boolean.class, timerId)).isTrue();
        assertThat(notificationCount(flow, "SLA_ESCALATION")).isPositive();
        assertThat(auditCount(flow, "SLA_ESCALATED")).isEqualTo(1);
    }

    @Test
    void controlDeadlineBreachGoesToCoordinator() {
        Flow flow = bootstrapWithManager("SMB");
        UUID timerId = jdbc.queryForObject(
                "select id from sla_timer where request_id = ? and kind = 'TECH_RESULT'",
                UUID.class, flow.requestId());

        jdbc.update("""
                        update sla_timer
                           set warned_at = ?, breached_at = null, escalation_level = 0,
                               warn_at = ?, due_at = ?, escalate_at = null, next_event_at = ?
                         where id = ?
                        """,
                ts(NOW.minusMinutes(1)), ts(NOW.minusMinutes(2)), ts(NOW.minusMinutes(1)),
                ts(NOW.minusMinutes(1)), timerId);

        monitor().processDueTimers();

        assertThat(jdbc.queryForObject(
                "select breached_at is not null from sla_timer where id = ?", Boolean.class, timerId)).isTrue();
        assertThat(notificationCount(flow, "CONTROL_DEADLINE_BREACH")).isPositive();
        assertThat(auditCount(flow, "CONTROL_DEADLINE_BREACH_RECORDED")).isEqualTo(1);
    }

    @Test
    void pauseLimitWarningAndReachedAreSentOnce() {
        Flow flow = bootstrapWithManager("SMB");
        UUID stageId = techCheckStage(flow);
        UUID timerId = timerId(stageId);

        UUID pauseId = UUID.randomUUID();
        jdbc.update("""
                        insert into sla_pause
                            (id, timer_id, reason_code, set_by, comment, started_at, limit_at, extended_count)
                        values (?, ?, 'AWAIT_CLIENT_DATA', 'SYSTEM', null, ?, ?, 0)
                        """,
                pauseId, timerId, ts(NOW.minusHours(1)), ts(NOW.plusHours(1)));
        jdbc.update("update sla_timer set status = 'PAUSED', next_event_at = ? where id = ?",
                ts(NOW.minusMinutes(1)), timerId);

        monitor().processDueTimers();
        assertThat(notificationCount(flow, "PAUSE_LIMIT_WARNING")).isPositive();
        assertThat(jdbc.queryForObject(
                "select next_event_at = ? from sla_timer where id = ?",
                Boolean.class, ts(NOW.plusHours(1)), timerId)).isTrue();
        assertThat(auditCount(flow, "PAUSE_LIMIT_WARNING_SENT")).isEqualTo(1);

        jdbc.update("update sla_pause set limit_at = ? where id = ?", ts(NOW.minusMinutes(1)), pauseId);
        jdbc.update("update sla_timer set next_event_at = ? where id = ?",
                ts(NOW.minusMinutes(1)), timerId);
        monitor().processDueTimers();

        assertThat(notificationCount(flow, "PAUSE_LIMIT_REACHED")).isPositive();
        assertThat(jdbc.queryForObject(
                "select next_event_at is null from sla_timer where id = ?", Boolean.class, timerId)).isTrue();
        assertThat(auditCount(flow, "PAUSE_LIMIT_REACHED")).isEqualTo(1);
    }

    private SlaMonitor monitor() {
        return new SlaMonitor(timerRepository, pauseRepository, requestRepository, stageRepository,
                notificationService, auditService, slaProperties,
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
    }

    private UUID timerId(UUID stageId) {
        return jdbc.queryForObject(
                "select id from sla_timer where stage_id = ? and kind = 'STAGE_SLA'",
                UUID.class, stageId);
    }

    private void updateTimer(UUID timerId, OffsetDateTime warnAt, OffsetDateTime dueAt,
                             OffsetDateTime escalateAt, OffsetDateTime nextEventAt) {
        jdbc.update("""
                        update sla_timer
                           set warn_at = ?, due_at = ?, escalate_at = ?, next_event_at = ?,
                               warned_at = null, breached_at = null, escalation_level = 0
                         where id = ?
                        """,
                ts(warnAt), ts(dueAt), ts(escalateAt), ts(nextEventAt), timerId);
    }

    private int notificationCount(Flow flow, String eventType) {
        return jdbc.queryForObject(
                "select count(*) from notification where request_id = ? and event_type = ?",
                Integer.class, flow.requestId(), eventType);
    }

    private int auditCount(Flow flow, String action) {
        return jdbc.queryForObject(
                "select count(*) from audit_log where request_id = ? and action = ?",
                Integer.class, flow.requestId(), action);
    }

    private static Timestamp ts(OffsetDateTime moment) {
        return Timestamp.from(moment.toInstant());
    }
}
