import type { SlaTimerView } from '../api/types';

// Состояние индикатора срока по sla.md §10: зелёный — израсходовано меньше 75%,
// жёлтый — от 75% до 100%, красный — нарушен, серый — на паузе.
// Считаем по снимку таймера из ответа ядра; сам срок (dueAt) рассчитывает ядро.

export type SlaState = 'ok' | 'warn' | 'breach' | 'paused';

const WORKDAY_MINUTES = 8 * 60;
const DAY_MINUTES = 24 * 60;

/** `3 р.д.`, `2 р.д. 6 ч`, `4 ч`, `1 ч 12 мин`, `25 мин`. */
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

export interface SlaSnapshot {
  state: SlaState;
  /** Доля израсходованного норматива, 0…1+. */
  share: number;
  normLabel: string;
  valueLabel: string;
  slaTimeLabel: string;
  calendarTimeLabel: string;
}

export function slaSnapshot(timer: SlaTimerView, now: number = Date.now()): SlaSnapshot {
  const work = timer.calendarKind !== 'CALENDAR';
  const elapsedMinutes = timer.elapsedSlaSeconds / 60;
  const share = timer.normMinutes > 0 ? elapsedMinutes / timer.normMinutes : 0;
  const remaining = timer.normMinutes - elapsedMinutes;
  const breached = remaining < 0;

  let state: SlaState = 'ok';
  if (timer.status === 'PAUSED') state = 'paused';
  else if (breached) state = 'breach';
  else if (share >= 0.75) state = 'warn';

  let valueLabel: string;
  if (state === 'paused') valueLabel = 'пауза';
  else if (breached) valueLabel = `просрочено на ${formatDuration(-remaining, work)}`;
  else valueLabel = `осталось ${formatDuration(remaining, work)}`;

  const started = Date.parse(timer.startedAt);
  const calendarMinutes = Number.isNaN(started) ? 0 : (now - started) / 60_000;

  return {
    state,
    share,
    normLabel: `норматив ${formatDuration(timer.normMinutes, work)}`,
    valueLabel,
    slaTimeLabel: formatDuration(elapsedMinutes, false),
    calendarTimeLabel: formatDuration(calendarMinutes, false),
  };
}
