import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatSpeed } from './format';

describe('форматы', () => {
  it('деньги из строки API', () => {
    expect(formatMoney('1200000.00')).toBe('1 200 000,00 ₽');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('дата без времени не сдвигается часовым поясом', () => {
    expect(formatDate('2026-11-11')).toBe('11.11.2026');
  });

  it('скорость', () => {
    expect(formatSpeed(100)).toBe('100 Мбит/с');
    expect(formatSpeed(undefined)).toBe('—');
  });
});
