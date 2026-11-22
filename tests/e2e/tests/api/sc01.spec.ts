import { expect, test } from '@playwright/test';
import { act, createDraft, demoPassword, getRequest, login, stageOf } from '../../lib/api';

// SC-01: ИП Соколов, стандартная заявка (docs/business/scenarios.md, раздел 4)
test.describe('SC-01 Стандартная заявка', () => {
  test.skip(!demoPassword, 'Нужны стенд с профилем DEMO и переменная DEMO_PASSWORD');

  test('от черновика до закрытия, шаги 1–10', async ({ request }) => {
    const client = await login(request, 'a.sokolov');
    const manager = await login(request, 'a.smirnova');
    const tech = await login(request, 'p.orlov');
    const engineer = await login(request, 'a.lebedev');
    const coordinator = await login(request, 'm.kovaleva'); // видит все заявки, читает карточку

    // Берёт свежую карточку заявки: id и version этапов нужны для следующего шага
    const fresh = (id: string) => getRequest(request, coordinator.token, id);

    let req = await test.step('1. Клиент создаёт черновик', async () => {
      const draft = await createDraft(request, client.token);
      expect(draft.status).toBe('DRAFT');
      return draft;
    });

    req = await test.step('2–3. Отправка, проверка данных, назначение менеджера', async () => {
      // submit синхронно проходит T01, T03 и T03b: в ответе уже «У менеджера»
      const sent = await act(request, client.token, `/requests/${req.id}/submit`, { version: req.version });
      expect(sent.status).toBe('WITH_MANAGER');
      expect(sent.integrationError).not.toBe(true);
      // managerId клиенту не отдаётся (core-api.yaml), смотрим карточку глазами координатора
      const card = await fresh(req.id);
      expect(card.managerId).toBe(manager.userId);
      return sent;
    });

    await test.step('4–5. Менеджер фиксирует типовые условия, создаётся этап «Проверка ТВ»', async () => {
      const withTerms = await act(request, manager.token, `/requests/${req.id}/commercial-terms`, {
        tariffType: 'STANDARD',
        contractType: 'OFFER',
        version: req.version,
      });
      expect(withTerms.status).toBe('TECH_CHECK');
      expect(stageOf(withTerms, 'TECH_CHECK').status).toBe('QUEUED');
      // Согласования не нужны: в маршруте нет этапов согласований
      const types = withTerms.stages?.map((s) => s.type) ?? [];
      for (const approval of ['LEGAL_APPROVAL', 'FINANCE_APPROVAL', 'PROCUREMENT_APPROVAL', 'MGMT_APPROVAL']) {
        expect(types).not.toContain(approval);
      }
    });

    await test.step('6. Тех. специалист берёт этап и вносит результат ТВ «есть»', async () => {
      const stage = stageOf(await fresh(req.id), 'TECH_CHECK');
      const taken = await act<{ version: number }>(request, tech.token, `/stages/${stage.id}/claim`, {
        version: stage.version,
      });
      await act(request, tech.token, `/stages/${stage.id}/tech-result`, {
        result: 'AVAILABLE',
        version: taken.version,
      });
      req = await fresh(req.id);
      expect(req.status).toBe('CONTRACT_SIGNING');
      expect(stageOf(req, 'CONTRACT_SIGNING')).toBeTruthy();
    });

    await test.step('7. Клиент акцептует оферту', async () => {
      const accepted = await act(request, client.token, `/requests/${req.id}/contract/accept-offer`, {
        version: req.version,
      });
      expect(accepted.status).toBe('IN_EXECUTION');
      expect(accepted.commitmentLevel).toBe(1);
      expect(stageOf(await fresh(req.id), 'INSTALLATION').status).toBe('QUEUED');
    });

    await test.step('8. Инженер берёт монтаж и завершает его', async () => {
      const stage = stageOf(await fresh(req.id), 'INSTALLATION');
      const taken = await act<{ version: number }>(request, engineer.token, `/stages/${stage.id}/claim`, {
        version: stage.version,
      });
      // Работы начаты: уровень обязательств 2
      expect((await fresh(req.id)).commitmentLevel).toBe(2);
      await act(request, engineer.token, `/stages/${stage.id}/complete`, { version: taken.version });
      expect(stageOf(await fresh(req.id), 'ACTIVATION').status).toBe('QUEUED');
    });

    await test.step('9. Инженер активирует услугу', async () => {
      const stage = stageOf(await fresh(req.id), 'ACTIVATION');
      const taken = await act<{ version: number }>(request, engineer.token, `/stages/${stage.id}/claim`, {
        version: stage.version,
      });
      await act(request, engineer.token, `/stages/${stage.id}/activate`, { version: taken.version });
      req = await fresh(req.id);
      expect(req.status).toBe('ACTIVATED');
      expect(req.commitmentLevel).toBe(3);
      expect(stageOf(req, 'CLOSING')).toBeTruthy();
    });

    await test.step('10. Менеджер отправляет акт и закрывает заявку', async () => {
      const sent = await act(request, manager.token, `/requests/${req.id}/act/sent`, { version: req.version });
      const closed = await act(request, manager.token, `/requests/${req.id}/act/signed`, {
        version: sent.version,
      });
      expect(closed.status).toBe('CLOSED');
      expect(closed.closedAt).toBeTruthy();
    });
  });
});
