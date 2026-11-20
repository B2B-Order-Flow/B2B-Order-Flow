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
    private static final Collection<SlaTimerStatus> ACTIVE = EnumSet.of(
            SlaTimerStatus.RUNNING, SlaTimerStatus.PAUSED);

    private final SlaTimerRepository timerRepository;
    private final SlaPauseRepository pauseRepository;
    private final SlaNormRepository normRepository;
    private final PauseReasonRepository pauseReasonRepository;
    private final RequestStageRepository stageRepository;
    private final SlaCalendarFactory calendarFactory;
    private final SlaProperties properties;
    private final Clock clock;

    public SlaTimerService(SlaTimerRepository timerRepository,
                           SlaPauseRepository pauseRepository,
                           SlaNormRepository normRepository,
                           PauseReasonRepository pauseReasonRepository,
                           RequestStageRepository stageRepository,
                           SlaCalendarFactory calendarFactory,
                           SlaProperties properties,
                           Clock clock) {
        this.timerRepository = timerRepository;
        this.pauseRepository = pauseRepository;
        this.normRepository = normRepository;
        this.pauseReasonRepository = pauseReasonRepository;
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
                + SlaTime.workingTimeBetween(timer.getStartedAt(), now, calendar).toSeconds();
        timer.setElapsedSlaSeconds(elapsed);
        timer.setStatus(SlaTimerStatus.PAUSED);

        OffsetDateTime pauseLimit = limitAt != null
                ? limitAt
                : SlaTime.addWorkingTime(now, pauseLimit(), calendar);
        timer.setNextEventAt(pauseLimit);
        timerRepository.saveAndFlush(timer);

        SlaPause pause = new SlaPause();
        pause.setTimerId(timer.getId());
        pause.setReasonCode(reasonCode);
        pause.setSetBy(actorId == null ? SlaPause.SET_BY_SYSTEM : actorId.toString());
        pause.setComment(comment == null || comment.isBlank()
                ? "Автопауза: " + reasonTitle(reasonCode)
                : comment);
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
                    + SlaTime.workingTimeBetween(timer.getStartedAt(), now, calendar).toSeconds();
            timer.setElapsedSlaSeconds(elapsed);
        }
        endActivePause(timer);
        timer.setStatus(SlaTimerStatus.STOPPED);
        timer.setNextEventAt(null);
        timerRepository.save(timer);
    }

    private SlaCalendar calendarFor(SlaTimer timer) {
        SlaNorm norm = timer.getStageId() == null
                ? controlNorm(timer.getKind())
                : stageNorm(stageRepository.findById(timer.getStageId())
                        .map(RequestStage::getType)
                        .orElse(null));
        return calendarFactory.forNorm(norm);
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

    private String reasonTitle(PauseReasonCode code) {
        return pauseReasonRepository.findById(code)
                .map(PauseReason::getTitle)
                .orElse(code.name());
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
