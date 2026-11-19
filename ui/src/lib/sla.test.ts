import { describe, expect, it } from 'vitest';
import { normalizePercent } from '../pages/request/CommercialTermsForm';
import { normalizeMoney } from '../pages/request/TechResultForm';
import { isValidInn, isValidPhone } from './requisites';
import type { SlaTimerView } from '../api/types';
import { formatDuration, slaSnapshot, stageTimer } from './sla';

// Таймер в форме ответа ядра: остаток и просрочку присылает ядро (SlaTimerView)
const timer = (elapsedMinutes: number, patch: Partial<SlaTimerView> = {}): SlaTimerView => ({
  id: 't-1',
  kind: 'STAGE_SLA',
  stageType: 'TECH_CHECK',
  status: 'RUNNING',
  normMinutes: 1440,
  startedAt: '2026-11-13T10:00:00Z',
  elapsedSlaSeconds: elapsedMinutes * 60,
  remainingSlaSeconds: (1440 - elapsedMinutes) * 60,
  calendarElapsedSeconds: 72 * 60,
  overdue: elapsedMinutes > 1440,
  ...patch,
});

describe('индикатор срока (sla.md §10)', () => {
  it('меньше 75% — в норме, от 75% — скоро, просрочка — от ядра, пауза — серый', () => {
    expect(slaSnapshot(timer(120)).state).toBe('ok');
    expect(slaSnapshot(timer(1080)).state).toBe('warn');
    expect(slaSnapshot(timer(1500)).state).toBe('breach');
    expect(slaSnapshot(timer(1500, { status: 'PAUSED' })).state).toBe('paused');
    // Просрочку решает ядро: признак overdue важнее расчёта по доле
    expect(slaSnapshot(timer(120, { overdue: true })).state).toBe('breach');
  });

  it('подписи как в макете: норматив и остаток в рабочих днях, время — из ядра', () => {
    const snapshot = slaSnapshot(timer(120));
    expect(snapshot.normLabel).toBe('норматив 3 р.д.');
    expect(snapshot.valueLabel).toBe('осталось 2 р.д. 6 ч');
    expect(snapshot.calendarTimeLabel).toBe('1 ч 12 мин');
    expect(slaSnapshot(timer(1500)).valueLabel).toBe('просрочено на 1 ч');
  });

  it('стройка и контрольный срок ТВ — в календарных днях', () => {
    expect(slaSnapshot(timer(0, { stageType: 'CONSTRUCTION', normMinutes: 14400 })).normLabel).toBe('норматив 10 д.');
    expect(slaSnapshot(timer(0, { kind: 'TECH_RESULT', stageType: undefined, normMinutes: 43200 })).normLabel).toBe(
      'норматив 30 д.',
    );
  });

  it('таймер этапа ищется по stageId среди таймеров заявки', () => {
    const timers = [timer(0, { kind: 'TECH_RESULT', id: 't-0' }), timer(0, { stageId: 's-1' })];
    expect(stageTimer(timers, 's-1')?.id).toBe('t-1');
    expect(stageTimer(timers, 's-2')).toBeUndefined();
  });

  it('длительности', () => {
    expect(formatDuration(72, false)).toBe('1 ч 12 мин');
    expect(formatDuration(25)).toBe('25 мин');
    expect(formatDuration(480)).toBe('1 р.д.');
  });
});

describe('форматы полей (api-contract.md §10)', () => {
  it('ИНН с контрольной суммой', () => {
    expect(isValidInn('7811001128')).toBe(true);
    expect(isValidInn('7811001129')).toBe(false);
    expect(isValidInn('770112345127')).toBe(true); // ИП Соколов из сида
    expect(isValidInn('770112345128')).toBe(false);
    expect(isValidInn('12345')).toBe(false);
  });

  it('телефон', () => {
    expect(isValidPhone('+7 (900) 000-00-00')).toBe(true);
    expect(isValidPhone('12345')).toBe(false);
    expect(isValidPhone('+7 900 abc')).toBe(false);
  });

  it('проценты и деньги строкой с двумя знаками', () => {
    expect(normalizePercent('10')).toBe('10.00');
    expect(normalizePercent('7,5')).toBe('7.50');
    expect(normalizePercent('101')).toBeNull();
    expect(normalizeMoney('1 200 000')).toBe('1200000.00');
    expect(normalizeMoney('12,345')).toBeNull();
  });
});
