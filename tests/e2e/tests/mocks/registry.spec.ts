import { test, expect } from '@playwright/test';
import { orgs, byName } from '../../fixtures/organizations';
import { registryUrl, correlationId } from '../../lib/mocks';

test.describe('Mock Справочник клиентов', () => {
  for (const org of orgs.filter((o) => o.inRegistry)) {
    test(`${org.name}: реквизиты и признак госзаказчика`, async ({ request }) => {
      const id = correlationId();
      const res = await request.get(registryUrl(org.inn), { headers: { 'X-Correlation-Id': id } });

      expect(res.status()).toBe(200);
      expect(res.headers()['x-correlation-id']).toBe(id);
      const body = await res.json();
      expect(body).toMatchObject({
        inn: org.inn,
        kpp: org.kpp,
        ogrn: org.ogrn,
        name: org.name,
        status: 'ACTIVE',
        isGovCustomer: org.gov,
      });
      expect(body.legalAddress).toBeTruthy();
    });
  }

  test('госзаказчик только один: ГБУ «Городская больница № 7» (R-02)', async ({ request }) => {
    const gov: string[] = [];
    for (const org of orgs.filter((o) => o.inRegistry)) {
      const body = await (await request.get(registryUrl(org.inn))).json();
      if (body.isGovCustomer) gov.push(org.name);
    }
    expect(gov).toEqual([byName('больница').name]);
  });

  test('«Фантом» не найден: 404 NOT_FOUND (SC-00 A1)', async ({ request }) => {
    const id = correlationId();
    const res = await request.get(registryUrl(byName('Фантом').inn), { headers: { 'X-Correlation-Id': id } });

    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    expect(await res.json()).toMatchObject({ code: 'NOT_FOUND', retryable: false, correlationId: id });
  });
});
