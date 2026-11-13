import { expect, test } from '@playwright/test';
import { createDraft, login, submit } from '../../lib/api';

// SC-01: ИП Соколов, стандартная заявка (docs/business/scenarios.md, раздел 4)
test.describe('SC-01 Стандартная заявка', () => {
  test('шаги 1–3: черновик, отправка, проверка данных, назначение менеджера', async ({ request }) => {
    const token = await login(request, 'a.sokolov');

    const draft = await createDraft(request, token);
    expect(draft.status).toBe('DRAFT');

    const res = await submit(request, token, draft.id, draft.version);
    expect(res.status()).toBe(200);

    // submit синхронно проходит T01, T03 и T03b: в ответе уже «У менеджера»
    const body = await res.json();
    expect(body.status).toBe('WITH_MANAGER');
    expect(body.integrationError).not.toBe(true);
  });

  // Остальные шаги подключим, когда Кирилл добавит эндпоинты в контракт ядра
  test.fixme('шаги 4–5: менеджер фиксирует типовые условия, создаётся этап «Проверка ТВ»', async () => {});
  test.fixme('шаг 6: тех. специалист вносит результат ТВ «есть»', async () => {});
  test.fixme('шаг 7: клиент акцептует оферту, заявка «В исполнении»', async () => {});
  test.fixme('шаги 8–9: инженер выполняет монтаж и активирует услугу', async () => {});
  test.fixme('шаг 10: менеджер закрывает заявку по подписанному акту', async () => {});
});
