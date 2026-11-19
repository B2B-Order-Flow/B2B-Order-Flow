import type { SlaTimerView } from '../api/types';

// Состояние индикатора срока по sla.md §10: зелёный — израсходовано меньше 75%,
// жёлтый — от 75% до 100%, красный — нарушен, серый — на паузе.
// Остаток, календарное время и просрочку считает ядро (SlaTimerView); интерфейс их только показывает.

export type SlaState = 'ok' | 'warn' | 'breach' | 'paused';

const WORKDAY_MINUTES = 8 * 60;
const DAY_MINUTES = 24 * 60;

/** `3 р.д.`, `2 р.д. 6 ч`, `4 ч`, `1 ч 12 мин`, `25 мин`; для календарных сроков — `д.`. */
export function formatDuration(totalMinutes: number, work = true): string {
  const minutes = Math.max(0, Math.round(totalMinutes));
  const dayLength = work ? WORKDAY_MINUTES : DAY_MINUTES;
  const dayUnit = work ? 'р.д.' : 'д.';
  const days = Math.floor(minutes / dayLength);
  const hours = Math.floor((minutes % dayLength) / 60);
  const rest = minutes % 60;
  if (days > 0) return hours > 0 ? `${days} ${dayUnit} ${hours} ч` : `${days} ${dayUnit}`;
  if (hours > 0) return rest > 0 ? `${hours} ч ${rest} мин` : `${hours} ч`;
  return `${rest} мин`;
}

/**
 * Календарные сроки: стройка (календарные дни, sla.md §4) и контрольный срок ТВ (30 календарных дней).
 * Остальные нормативы — в рабочем времени.
 */
export function isCalendarTimer(timer: Pick<SlaTimerView, 'kind' | 'stageType'>): boolean {
  return timer.kind === 'TECH_RESULT' || timer.stageType === 'CONSTRUCTION';
}

export interface SlaSnapshot {
  state: SlaState;
  /** Доля израсходованного норматива, 0…1+. */
  share: number;
  normLabel: string;
  valueLabel: string;
  /** Время по SLA и календарное — подписи для карточки; пусто, если ядро их не прислало. */
  slaTimeLabel: string | null;
  calendarTimeLabel: string | null;
}

export function slaSnapshot(timer: SlaTimerView): SlaSnapshot {
  const work = !isCalendarTimer(timer);
  const normSeconds = timer.normMinutes ? timer.normMinutes * 60 : null;
  const elapsed =
    timer.elapsedSlaSeconds ??
    (normSeconds !== null && timer.remainingSlaSeconds !== undefined ? normSeconds - timer.remainingSlaSeconds : null);
  const share = normSeconds && elapsed !== null ? elapsed / normSeconds : timer.overdue ? 1 : 0;

  let state: SlaState = 'ok';
  if (timer.status === 'PAUSED') state = 'paused';
  else if (timer.overdue) state = 'breach';
  else if (timer.warnedAt || share >= 0.75) state = 'warn';

  const remaining = timer.remainingSlaSeconds;
  let valueLabel: string;
  if (state === 'paused') valueLabel = 'пауза';
  else if (timer.overdue) {
    // Насколько просрочено: отрицательный остаток или время с момента нарушения
    const overSeconds =
      remaining !== undefined && remaining < 0
        ? -remaining
        : timer.breachedAt
          ? (Date.now() - Date.parse(timer.breachedAt)) / 1000
          : null;
    valueLabel = overSeconds !== null ? `просрочено на ${formatDuration(overSeconds / 60, work)}` : 'просрочено';
  } else if (remaining !== undefined) valueLabel = `осталось ${formatDuration(remaining / 60, work)}`;
  else valueLabel = '—';

  return {
    state,
    share,
    normLabel: timer.normMinutes ? `норматив ${formatDuration(timer.normMinutes, work)}` : 'норматив не указан',
    valueLabel,
    slaTimeLabel: timer.elapsedSlaSeconds !== undefined ? formatDuration(timer.elapsedSlaSeconds / 60, false) : null,
    calendarTimeLabel:
      timer.calendarElapsedSeconds !== undefined ? formatDuration(timer.calendarElapsedSeconds / 60, false) : null,
  };
}

/** Таймер срока этапа из ответа GET /requests/{id}/sla. */
export function stageTimer(timers: SlaTimerView[] | undefined, stageId: string): SlaTimerView | undefined {
  return timers?.find((t) => t.kind === 'STAGE_SLA' && t.stageId === stageId);
}
