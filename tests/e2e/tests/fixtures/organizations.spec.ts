import { test, expect } from '@playwright/test';
import { orgs } from '../../fixtures/organizations';
import { innIsValid, ogrnIsValid } from '../../lib/inn';

test.describe('Тестовые организации', () => {
  for (const org of orgs) {
    test(`${org.name}: ИНН проходит контрольную сумму`, () => {
      expect(innIsValid(org.inn)).toBe(true);
    });

    if (org.ogrn) {
      test(`${org.name}: ОГРН проходит контрольную сумму`, () => {
        expect(ogrnIsValid(org.ogrn!)).toBe(true);
      });
    }
  }

  test('ИНН не повторяются', () => {
    const inns = orgs.map((o) => o.inn);
    expect(new Set(inns).size).toBe(inns.length);
  });

  test('проверка ловит неверную контрольную сумму', () => {
    // Старые ИНН сбоев из первой версии integrations.md
    expect(innIsValid('9999000019')).toBe(false);
    expect(innIsValid('9999000027')).toBe(false);
  });
});
