// Проверка контрольных сумм по алгоритму ФНС. Ядро проверяет ИНН локально до обращения к мокам,
// поэтому фикстура с неверной суммой до мока не дойдёт.

export function innIsValid(inn: string): boolean {
  if (!/^\d+$/.test(inn)) return false;
  const d = [...inn].map(Number);
  const check = (w: number[], i: number) => (w.reduce((s, k, j) => s + k * d[j], 0) % 11) % 10 === d[i];
  if (d.length === 10) return check([2, 4, 10, 3, 5, 9, 4, 6, 8], 9);
  if (d.length === 12) {
    return check([7, 2, 4, 10, 3, 5, 9, 4, 6, 8], 10) && check([3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8], 11);
  }
  return false;
}

export function ogrnIsValid(ogrn: string): boolean {
  if (!/^\d+$/.test(ogrn)) return false;
  if (ogrn.length === 13) return Number((BigInt(ogrn.slice(0, 12)) % 11n) % 10n) === Number(ogrn[12]);
  if (ogrn.length === 15) return Number((BigInt(ogrn.slice(0, 14)) % 13n) % 10n) === Number(ogrn[14]);
  return false;
}
