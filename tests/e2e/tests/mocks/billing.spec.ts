import { test, expect } from '@playwright/test';
import { orgs, byName } from '../../fixtures/organizations';
import { billingUrl, correlationId } from '../../lib/mocks';

test.describe('Mock Биллинг', () => {
  for (const org of orgs.filter((o) => o.debt !== null)) {
    test(`${org.name}: задолженность ${org.debt}`, async ({ request }) => {
      const id = correlationId();
      const res = await request.get(billingUrl(org.inn), { headers: { 'X-Correlation-Id': id } });

      expect(res.status()).toBe(200);
      expect(res.headers()['x-correlation-id']).toBe(id);
      expect(await res.json()).toMatchObject({ inn: org.inn, debtAmount: org.debt, currency: 'RUB' });
    });
  }

  test('задолженность выше нуля только у «Северторга» (EC-03)', async ({ request }) => {
    const withDebt: string[] = [];
    for (const org of orgs.filter((o) => o.debt !== null)) {
      const body = await (await request.get(billingUrl(org.inn))).json();
      if (Number(body.debtAmount) > 0) withDebt.push(org.name);
    }
    expect(withDebt).toEqual([byName('Северторг').name]);
  });

  test('нет лицевого счёта: 404 NOT_FOUND (ядро трактует как долг 0.00)', async ({ request }) => {
    const id = correlationId();
    const res = await request.get(billingUrl(byName('Фантом').inn), { headers: { 'X-Correlation-Id': id } });

    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    expect(await res.json()).toMatchObject({ code: 'NOT_FOUND', correlationId: id });
  });
});
