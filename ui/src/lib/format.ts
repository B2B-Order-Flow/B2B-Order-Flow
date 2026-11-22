// Форматы на экране — ui-requirements.md §4.5. В API даты в UTC (ISO 8601), на экране — локальное время.

const dateTimeFormat = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const dateFormat = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });

const moneyFormat = new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const EMPTY = '—';

/** `11.11.2026 14:05` в локальном времени. */
export function formatDateTime(iso: string | undefined | null): string {
  if (!iso) return EMPTY;
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? EMPTY : dateTimeFormat.format(date).replace(',', '');
}

/** `11.11.2026`. Дата без времени (`2026-11-11`) не сдвигается часовым поясом. */
export function formatDate(value: string | undefined | null): string {
  if (!value) return EMPTY;
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.exec(value);
  const date = dateOnly ? new Date(`${value}T00:00:00`) : new Date(value);
  return Number.isNaN(date.getTime()) ? EMPTY : dateFormat.format(date);
}

/** Деньги приходят строкой `"1200000.00"` → `1 200 000,00 ₽`. */
export function formatMoney(value: string | number | undefined | null): string {
  if (value === undefined || value === null || value === '') return EMPTY;
  const amount = typeof value === 'number' ? value : Number(value);
  // Intl ставит неразрывные пробелы — заменяем обычными, чтобы текст одинаково копировался и искался
  return Number.isNaN(amount) ? EMPTY : `${moneyFormat.format(amount).replace(/\s/g, ' ')} ₽`;
}

/** `100 Мбит/с`. */
export function formatSpeed(mbps: number | undefined | null): string {
  return mbps === undefined || mbps === null ? EMPTY : `${mbps} Мбит/с`;
}

const shortDateTimeFormat = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

/** `11.11 09:10` — для таблиц очередей, где год очевиден. */
export function formatShortDateTime(iso: string | undefined | null): string {
  if (!iso) return EMPTY;
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? EMPTY : shortDateTimeFormat.format(date).replace(',', '');
}

/** Сегодня в локальном времени, `YYYY-MM-DD` — для полей даты. */
export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const dateInZone = (timeZone: string, now: Date) =>
  new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);

/**
 * Границы даты договора по календарю ядра: «сегодня» там считается в UTC (DEMO) или по Москве (PROD),
 * а профиль интерфейсу неизвестен. `initial` примут оба календаря, `max` — последняя дата, возможная в PROD.
 */
export function contractDateBounds(now: Date = new Date()): { initial: string; max: string } {
  return { initial: dateInZone('UTC', now), max: dateInZone('Europe/Moscow', now) };
}
