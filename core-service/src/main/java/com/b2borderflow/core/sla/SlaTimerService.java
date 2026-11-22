package com.b2borderflow.core.sla;

import com.b2borderflow.core.config.SlaProperties;
import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.RequestStage;
import com.b2borderflow.core.request.RequestStageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Жизненный цикл таймеров этапов и контрольных сроков: запуск, пауза, остановка.
 *
 * <p>Календарь выбирается по нормативу через {@link SlaCalendarFactory}, то есть
 * по {@code sla_norm.calendar_kind}, а не только по профилю: в {@code PROD}
 * нормативы с {@code CALENDAR} (например, контрольный срок ТВ) считаются
 * календарными минутами (docs/system/sla.md, 3.1). Планировщик и обработка
 * предупреждений/эскалаций — следующая часть SLA Monitor.</p>
 */
@Service
public class SlaTimerService {

    private static final Duration PROD_PAUSE_LIMIT = Duration.ofHours(80);
    private static final Duration DEMO_PAUSE_LIMIT = Duration.ofMinutes(4);
    private static final Duration PROD_PAUSE_WARNING_BEFORE = Duration.ofHours(16);
    private static final Duration DEMO_PAUSE_WARNING_BEFORE = Duration.ofMinutes(1);
    private static final Collection<SlaTimerStatus> ACTIVE = EnumSet.of(
            SlaTimerStatus.RUNNING, SlaTimerStatus.PAUSED);

    private final SlaTimerRepository timerRepository;
    private final SlaPauseRepository pauseRepository;
    private final SlaNormRepository normRepository;
    private final RequestStageRepository stageRepository;
    private final SlaCalendarFactory calendarFactory;
    private final SlaProperties properties;
    private final Clock clock;

    public SlaTimerService(SlaTimerRepository timerRepository,
                           SlaPauseRepository pauseRepository,
                           SlaNormRepository normRepository,
                           RequestStageRepository stageRepository,
                           SlaCalendarFactory calendarFactory,
                           SlaProperties properties,
                           Clock clock) {
        this.timerRepository = timerRepository;
        this.pauseRepository = pauseRepository;
        this.normRepository = normRepository;
        this.stageRepository = stageRepository;
        this.calendarFactory = calendarFactory;
        this.properties = properties;
        this.clock = clock;
    }

    /** Стартует SLA этапа: с {@code QUEUED}, для CONTRACT_SIGNING/CLOSING — с создания. */
    @Transactional
    public SlaTimer startStageTimer(Request request, RequestStage stage) {
        SlaNorm norm = stageNorm(stage.getType());
        return startTimer(request.getId(), stage.getId(), SlaTimerKind.STAGE_SLA, norm);
    }

    /** Контрольный срок заявки (проверка данных, назначение менеджера, ТВ, приёмка). */
    @Transactional
    public SlaTimer startControlTimer(Request request, SlaTimerKind kind) {
        SlaNorm norm = controlNorm(kind);
        return startTimer(request.getId(), null, kind, norm);
    }

    @Transactional
    public void stopStageTimer(RequestStage stage) {
        findActiveStageTimer(stage.getId()).ifPresent(timer -> stop(timer, now()));
    }

    @Transactional
    public void stopControlTimer(UUID requestId, SlaTimerKind kind) {
        timerRepository.findFirstByRequestIdAndKindAndStatusIn(requestId, kind, ACTIVE)
                .ifPresent(timer -> stop(timer, now()));
    }

    /** Останавливает все активные таймеры заявки — терминальный переход T18. */
    @Transactional
    public void stopRequestTimers(UUID requestId) {
        OffsetDateTime now = now();
        timerRepository.findByRequestIdAndStatusIn(requestId, ACTIVE)
                .forEach(timer -> stop(timer, now));
    }

    /**
     * Автопауза «Ожидание подписи клиента». {@code limitAt} можно передать заранее —
     * для акта вместо обычного предела паузы действует срок приёмки (sla.md, 6.2).
     */
    @Transactional
    public SlaPause pauseForClientSignature(RequestStage stage, OffsetDateTime limitAt) {
        return pause(stage, PauseReasonCode.AWAIT_CLIENT_SIGNATURE,
                "Ожидание подписи клиента", null, limitAt);
    }

    @Transactional
    public SlaPause pause(RequestStage stage, PauseReasonCode reasonCode, String comment,
                          UUID actorId, OffsetDateTime limitAt) {
        Optional<SlaTimer> active = findActiveStageTimer(stage.getId());
        if (active.isEmpty()) {
            return null;
        }
        SlaTimer timer = active.get();
        if (timer.getStatus() == SlaTimerStatus.PAUSED) {
            return pauseRepository.findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(timer.getId())
                    .orElse(null);
        }

        OffsetDateTime now = now();
        SlaCalendar calendar = calendarFor(timer);
        long elapsed = timer.getElapsedSlaSeconds()
                + SlaTime.workingTimeBetween(elapsedAnchor(timer), now, calendar).toSeconds();
        timer.setElapsedSlaSeconds(elapsed);
        timer.setStatus(SlaTimerStatus.PAUSED);

        OffsetDateTime pauseLimit = limitAt != null
                ? limitAt
                : SlaTime.addWorkingTime(now, pauseLimit(), calendar);
        // Планировщик просыпается и за 2 рабочих дня до предела, и в сам предел (sla.md, 5.2).
        Duration untilLimit = SlaTime.workingTimeBetween(now, pauseLimit, calendar);
        Duration warningBefore = pauseWarningBefore();
        Duration untilWarning = untilLimit.compareTo(warningBefore) > 0
                ? untilLimit.minus(warningBefore)
                : Duration.ZERO;
        OffsetDateTime warnAt = SlaTime.addWorkingTime(now, untilWarning, calendar);
        timer.setNextEventAt(earliest(warnAt, pauseLimit));
        timerRepository.saveAndFlush(timer);

        SlaPause pause = new SlaPause();
        pause.setTimerId(timer.getId());
        pause.setReasonCode(reasonCode);
        pause.setSetBy(actorId == null ? SlaPause.SET_BY_SYSTEM : actorId.toString());
        pause.setComment(comment == null || comment.isBlank() ? null : comment);
        pause.setStartedAt(now);
        pause.setLimitAt(pauseLimit);
        return pauseRepository.save(pause);
    }

    @Transactional
    public void endActivePause(SlaTimer timer) {
        OffsetDateTime now = now();
        pauseRepository.findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(timer.getId())
                .ifPresent(pause -> {
                    pause.setEndedAt(now);
                    pauseRepository.save(pause);
                });
    }

    public Optional<SlaTimer> findActiveStageTimer(UUID stageId) {
        return timerRepository.findFirstByStageIdAndStatusIn(stageId, ACTIVE);
    }

    public Optional<SlaTimer> findAcceptanceTimer(UUID requestId) {
        return timerRepository.findFirstByRequestIdAndKindOrderByCreatedAtDesc(
                requestId, SlaTimerKind.ACCEPTANCE);
    }

    private SlaTimer startTimer(UUID requestId, UUID stageId, SlaTimerKind kind, SlaNorm norm) {
        OffsetDateTime now = now();
        SlaCalendar calendar = calendarFactory.forNorm(norm);
        int normMinutes = norm.getNormMinutes();

        OffsetDateTime dueAt = SlaTime.addWorkingTime(now, Duration.ofMinutes(normMinutes), calendar);
        int remainingBeforeWarning = Math.max(0, normMinutes - norm.getWarnBeforeMinutes());
        OffsetDateTime warnAt = SlaTime.addWorkingTime(now, Duration.ofMinutes(remainingBeforeWarning), calendar);
        OffsetDateTime escalateAt = SlaTime.addWorkingTime(
                dueAt, Duration.ofMinutes(Math.max(1, normMinutes / 2)), calendar);

        SlaTimer timer = new SlaTimer();
        timer.setRequestId(requestId);
        timer.setStageId(stageId);
        timer.setKind(kind);
        timer.setNormMinutes(normMinutes);
        timer.setStatus(SlaTimerStatus.RUNNING);
        timer.setStartedAt(now);
        timer.setWarnAt(warnAt);
        timer.setDueAt(dueAt);
        timer.setEscalateAt(escalateAt);
        timer.setElapsedSlaSeconds(0);
        timer.setNextEventAt(earliest(warnAt, dueAt, escalateAt));
        return timerRepository.saveAndFlush(timer);
    }

    private void stop(SlaTimer timer, OffsetDateTime now) {
        if (timer.getStatus() == SlaTimerStatus.RUNNING) {
            SlaCalendar calendar = calendarFor(timer);
            long elapsed = timer.getElapsedSlaSeconds()
                    + SlaTime.workingTimeBetween(elapsedAnchor(timer), now, calendar).toSeconds();
            timer.setElapsedSlaSeconds(elapsed);
        }
        endActivePause(timer);
        timer.setStatus(SlaTimerStatus.STOPPED);
        timer.setNextEventAt(null);
        timerRepository.save(timer);
    }

    /**
     * Точка отсчёта для накопления SLA. После первой паузы это {@code ended_at}
     * последней завершённой паузы, иначе {@code started_at}: иначе время до паузы
     * посчиталось бы ещё раз вместе с паузой.
     */
    public OffsetDateTime elapsedAnchor(SlaTimer timer) {
        return pauseRepository.findFirstByTimerIdAndEndedAtIsNotNullOrderByEndedAtDesc(timer.getId())
                .map(SlaPause::getEndedAt)
                .orElse(timer.getStartedAt());
    }

    /** Израсходованное рабочее время с учётом уже накопленного и текущего интервала. */
    public long elapsedSlaSeconds(SlaTimer timer, OffsetDateTime at) {
        if (timer.getStatus() != SlaTimerStatus.RUNNING) {
            return timer.getElapsedSlaSeconds();
        }
        return timer.getElapsedSlaSeconds()
                + SlaTime.workingTimeBetween(elapsedAnchor(timer), at, calendarFor(timer)).toSeconds();
    }

    public Optional<SlaPause> activePause(UUID timerId) {
        return pauseRepository.findFirstByTimerIdAndEndedAtIsNullOrderByStartedAtDesc(timerId);
    }

    /** Снятие ручной паузы: отсчёт продолжается по остатку норматива. */
    @Transactional
    public boolean resume(SlaTimer timer) {
        Optional<SlaPause> active = activePause(timer.getId());
        if (active.isEmpty()) {
            return false;
        }
        OffsetDateTime now = now();
        SlaCalendar calendar = calendarFor(timer);
        SlaNorm norm = normFor(timer);
        endActivePause(timer);

        long remainingSeconds = Math.max(0, timer.getNormMinutes() * 60L - timer.getElapsedSlaSeconds());
        OffsetDateTime dueAt = SlaTime.addWorkingTime(
                now, Duration.ofSeconds(remainingSeconds), calendar);
        long warningSeconds = Math.max(0, remainingSeconds - norm.getWarnBeforeMinutes() * 60L);
        OffsetDateTime warnAt = SlaTime.addWorkingTime(
                now, Duration.ofSeconds(warningSeconds), calendar);
        OffsetDateTime escalateAt = SlaTime.addWorkingTime(
                dueAt, Duration.ofMinutes(Math.max(1, timer.getNormMinutes() / 2)), calendar);

        timer.setStatus(SlaTimerStatus.RUNNING);
        timer.setDueAt(dueAt);
        timer.setWarnAt(warnAt);
        timer.setEscalateAt(escalateAt);
        timer.setNextEventAt(earliest(warnAt, dueAt, escalateAt));
        timerRepository.save(timer);
        return true;
    }

    /** Продление паузы: предел сдвигается на стандартный предел, планировщик получает новое событие. */
    @Transactional
    public boolean extendPause(SlaTimer timer, UUID actorId) {
        Optional<SlaPause> active = activePause(timer.getId());
        if (active.isEmpty()) {
            return false;
        }
        SlaPause pause = active.get();
        OffsetDateTime now = now();
        SlaCalendar calendar = calendarFor(timer);
        OffsetDateTime newLimit = SlaTime.addWorkingTime(pause.getLimitAt(), pauseLimit(), calendar);

        pause.setLimitAt(newLimit);
        pause.setExtendedCount(pause.getExtendedCount() + 1);
        pause.setExtendedBy(actorId);
        pauseRepository.save(pause);

        Duration untilLimit = SlaTime.workingTimeBetween(now, newLimit, calendar);
        Duration warningBefore = pauseWarningBefore();
        Duration untilWarning = untilLimit.compareTo(warningBefore) > 0
                ? untilLimit.minus(warningBefore)
                : Duration.ZERO;
        OffsetDateTime warnAt = SlaTime.addWorkingTime(now, untilWarning, calendar);
        timer.setNextEventAt(earliest(warnAt, newLimit));
        timerRepository.save(timer);
        return true;
    }

    public SlaCalendar calendarFor(SlaTimer timer) {
        return calendarFactory.forNorm(normFor(timer));
    }

    private SlaNorm normFor(SlaTimer timer) {
        return timer.getStageId() == null
                ? controlNorm(timer.getKind())
                : stageNorm(stageRepository.findById(timer.getStageId())
                        .map(RequestStage::getType)
                        .orElse(null));
    }

    private SlaNorm stageNorm(com.b2borderflow.core.request.RequestStageType stageType) {
        return normRepository.findByProfileAndKindAndStageType(
                        properties.getProfile(), SlaTimerKind.STAGE_SLA, stageType)
                .or(() -> normRepository.findByProfileAndKindAndStageTypeIsNull(
                        properties.getProfile(), SlaTimerKind.STAGE_SLA))
                .orElseThrow(() -> new IllegalStateException(
                        "Нет норматива SLA для этапа " + stageType + " в профиле " + properties.getProfile()));
    }

    private SlaNorm controlNorm(SlaTimerKind kind) {
        return normRepository.findByProfileAndKindAndStageTypeIsNull(properties.getProfile(), kind)
                .orElseThrow(() -> new IllegalStateException(
                        "Нет норматива SLA " + kind + " в профиле " + properties.getProfile()));
    }

    private Duration pauseLimit() {
        return properties.getProfile() == SlaProfile.DEMO ? DEMO_PAUSE_LIMIT : PROD_PAUSE_LIMIT;
    }

    private Duration pauseWarningBefore() {
        return properties.getProfile() == SlaProfile.DEMO
                ? DEMO_PAUSE_WARNING_BEFORE
                : PROD_PAUSE_WARNING_BEFORE;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static OffsetDateTime earliest(OffsetDateTime... moments) {
        OffsetDateTime result = null;
        for (OffsetDateTime moment : moments) {
            if (moment != null && (result == null || moment.isBefore(result))) {
                result = moment;
            }
        }
        return result;
    }
}
