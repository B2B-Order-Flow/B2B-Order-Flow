import { expect, test } from '@playwright/test';
import { act, createDraft, demoPassword, getRequest, login } from '../../lib/api';

test.describe('SC-02 Заявка с дополнительными согласованиями', () => {
  test.skip(!demoPassword, 'Нужны стенд с профилем DEMO и переменная DEMO_PASSWORD');

  test('шаги 1–3: от черновика «Логистик Плюс» до назначения менеджера', async ({ request }) => {
    const client = await login(request, 'n.fedorova');
    const manager = await login(request, 'a.smirnova');
    const draft = await createDraft(request, client.token, {
      address: 'г. Екатеринбург, ул. Монтажников, 1',
      requestedSpeedMbps: 500,
      contactName: 'Наталья Фёдорова',
      contractPreference: 'CLIENT_FORM',
      clientComment: 'Просим скидку на подключение склада',
    });
    expect(draft.status).toBe('DRAFT');

    const sent = await act(request, client.token, `/requests/${draft.id}/submit`, { version: draft.version });
    expect(sent.status).toBe('WITH_MANAGER');

    const card = await getRequest(request, manager.token, draft.id);
    expect(card).toMatchObject({
      status: 'WITH_MANAGER',
      managerId: manager.userId,
      companyName: 'ООО «Логистик Плюс»',
      createdBy: client.userId,
      segment: 'LARGE',
      isGovCustomer: false,
      debtAmount: '0.00',
      integrationError: false,
      requestedSpeedMbps: 500,
      contractPreference: 'CLIENT_FORM',
    });
  });
});
