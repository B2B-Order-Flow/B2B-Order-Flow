// Проверка реквизитов до отправки (api-contract.md §10.4). Окончательно проверяют шлюз и ядро.

const digits = (value: string) => value.split('').map(Number);

function checksum(values: number[], weights: number[]): number {
  const sum = weights.reduce((acc, weight, index) => acc + weight * values[index]!, 0);
  return (sum % 11) % 10;
}

/** ИНН: 10 цифр (организация) или 12 (ИП) с контрольной суммой. */
export function isValidInn(value: string): boolean {
  if (!/^\d{10}$|^\d{12}$/.test(value)) return false;
  const d = digits(value);
  if (d.length === 10) return checksum(d, [2, 4, 10, 3, 5, 9, 4, 6, 8]) === d[9];
  return (
    checksum(d, [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]) === d[10] && checksum(d, [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]) === d[11]
  );
}

export const isValidKpp = (value: string) => /^\d{9}$/.test(value);

export const isValidOgrn = (value: string) => /^\d{13}$|^\d{15}$/.test(value);

/** Телефон: до 32 символов, не менее 10 цифр; кроме цифр — «+» в начале, скобки, дефисы, пробелы (§10.6). */
export function isValidPhone(value: string): boolean {
  if (value.length > 32 || !/^\+?[\d\s()-]+$/.test(value)) return false;
  return value.replace(/\D/g, '').length >= 10;
}
