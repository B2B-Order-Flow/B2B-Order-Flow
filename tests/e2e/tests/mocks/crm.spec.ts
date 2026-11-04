import { test, expect } from '@playwright/test';
import { orgs, byName } from '../../fixtures/organizations';
import { crmUrl, correlationId, resetCrmScenarios } from '../../lib/mocks';

const headers = (id: string) => ({ 'X-Correlation-Id': id });

test.describe('Mock CRM: обычные ответы', () => {
  for (const org of orgs.filter((o) => o.crm === 'ok')) {
    test(`${org.name}: сегмент и менеджер`, async ({ request }) => {
      const id = correlationId();
      const res = await request.get(crmUrl(org.inn), { headers: headers(id) });

      expect(res.status()).toBe(200);
      expect(res.headers()['x-correlation-id']).toBe(id);
      const body = await res.json();
      expect(body).toMatchObject({
        inn: org.inn,
        name: org.name,
        segment: org.segment,
        assignedManagerLogin: org.manager,
      });
      expect(body.updatedAt).toBeTruthy();
    });
  }
});

test.describe('Mock CRM: клиента нет', () => {
  test('неизвестный ИНН даёт 404 NOT_FOUND в формате problem+json', async ({ request }) => {
    const id = correlationId();
    const res = await request.get(crmUrl(byName('Фантом').inn), { headers: headers(id) });

    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    expect(await res.json()).toMatchObject({
      code: 'NOT_FOUND',
      status: 404,
      retryable: false,
      correlationId: id,
    });
  });
});

test.describe('Mock CRM: управляемые сбои', () => {
  test('«Таймаут»: ответ не приходит за 2 секунды', async ({ request }) => {
    const call = request.get(crmUrl(byName('Таймаут').inn), { timeout: 2000 });
    await expect(call).rejects.toThrow(/timeout/i);
  });

  test('«Медленный»: ответ приходит через 1,5 с, в пределах таймаута', async ({ request }) => {
    const started = Date.now();
    const res = await request.get(crmUrl(byName('Медленный').inn), { timeout: 2000 });
    const elapsed = Date.now() - started;

    expect(res.status()).toBe(200);
    expect(elapsed).toBeGreaterThanOrEqual(1400);
    expect(elapsed).toBeLessThan(2000);
  });

  test('«Глухой»: всегда 503 с retryable=true', async ({ request }) => {
    for (let attempt = 1; attempt <= 3; attempt++) {
      const res = await request.get(crmUrl(byName('Глухой').inn));
      expect(res.status(), `попытка ${attempt}`).toBe(503);
      expect(await res.json()).toMatchObject({ code: 'SERVICE_UNAVAILABLE', retryable: true });
    }
  });

  test.describe('«Шаткий»: 503, 503, затем 200', () => {
    test.beforeEach(async ({ request }) => resetCrmScenarios(request));
    test.afterAll(async ({ request }) => resetCrmScenarios(request));

    test('повторы доводят дело до успеха', async ({ request }) => {
      const url = crmUrl(byName('Шаткий').inn);
      const statuses: number[] = [];
      for (let attempt = 0; attempt < 4; attempt++) {
        statuses.push((await request.get(url)).status());
      }
      // После третьей попытки сценарий остаётся в состоянии «восстановился»
      expect(statuses).toEqual([503, 503, 200, 200]);
    });

    test('после сброса сценарий начинается заново', async ({ request }) => {
      const url = crmUrl(byName('Шаткий').inn);
      await request.get(url);
      await request.get(url);
      expect((await request.get(url)).status()).toBe(200);

      await resetCrmScenarios(request);
      expect((await request.get(url)).status()).toBe(503);
    });
  });
});
