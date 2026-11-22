import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { NotificationView, RequestView, Role, UserView } from './api/types';
import { notificationTarget, notificationText } from './lib/notifications';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// UI-14: колокольчик, список, прочтение (ui-requirements.md §7.4, notifications.md).

const t = texts.notifications;

const manager: UserView = {
  id: 'u-1',
  login: 'a.smirnova',
  fullName: 'Анна Смирнова',
  email: 'a@example.test',
  operatorRoles: ['SALES_MANAGER'],
};
const admin: UserView = { ...manager, id: 'u-admin', login: 'admin', operatorRoles: ['ADMIN'] };

const request: RequestView = {
  id: 'r-1',
  number: 'REQ-2026-000300',
  companyId: 'c-1',
  status: 'WITH_MANAGER',
  version: 1,
  managerId: 'u-1',
  createdAt: '2026-11-13T11:00:00Z',
  address: 'г. Москва, ул. Лесная, д. 3',
};

const note = (patch: Partial<NotificationView>): NotificationView => ({
  id: 'n-1',
  eventType: 'REQUEST_ASSIGNED',
  requestId: 'r-1',
  createdAt: '2026-11-22T09:00:00Z',
  ...patch,
});

const pageOf = (content: NotificationView[], total = content.length, size = 20, number = 0) => ({
  content,
  page: { number, size, totalElements: total, totalPages: Math.max(1, Math.ceil(total / size)) },
});

function api(initial: NotificationView[], user: UserView = manager, role: Role = 'SALES_MANAGER') {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
  const state = { items: initial };
  const readOne = (id: string) => {
    state.items = state.items.map((item) => (item.id === id ? { ...item, readAt: '2026-11-22T10:00:00Z' } : item));
  };
  const handlers: Parameters<typeof mockApi>[0] = {
    'GET /me': () => jsonResponse(200, user),
    'GET /requests': () => jsonResponse(200, pageOf([])),
    'GET /requests/r-1': () => jsonResponse(200, request),
    'GET /requests/r-1/history': () => jsonResponse(200, pageOf([])),
    'GET /notifications': (url) => {
      const size = Number(url.searchParams.get('size') ?? 20);
      const list = url.searchParams.get('unread') === 'true' ? state.items.filter((i) => !i.readAt) : state.items;
      return jsonResponse(200, pageOf(list.slice(0, size), list.length, size));
    },
    'POST /notifications/read-all': () => {
      state.items = state.items.map((item) => ({ ...item, readAt: item.readAt ?? '2026-11-22T10:00:00Z' }));
      return new Response(null, { status: 204 });
    },
  };
  for (const item of initial) {
    handlers[`POST /notifications/${item.id}/read`] = () => {
      readOne(item.id);
      return new Response(null, { status: 204 });
    };
  }
  return { fetchMock: mockApi(handlers), state };
}

const posted = (fetchMock: ReturnType<typeof mockApi>, suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && init?.method === 'POST');

describe('UI-14. Колокольчик', () => {
  it('показывает число непрочитанных и последние уведомления с номером заявки', async () => {
    api([note({ id: 'n-1' }), note({ id: 'n-2', readAt: '2026-11-22T09:30:00Z' })]);
    const user = userEvent.setup();
    renderApp('/requests');

    expect(await screen.findByTestId('notifications-badge')).toHaveTextContent('1');
    await user.click(screen.getByTestId('notifications-bell'));

    const popover = await screen.findByTestId('notifications-popover');
    const row = await within(popover).findByTestId('notifications-menu-row-n-1');
    expect(row).toHaveAttribute('data-state', 'unread');
    expect(await within(row).findByText('Вам назначена заявка REQ-2026-000300')).toBeInTheDocument();
  });

  it('клик открывает карточку и отмечает уведомление прочитанным', async () => {
    const { fetchMock } = api([note({})]);
    const user = userEvent.setup();
    const { router } = renderApp('/requests');

    await user.click(await screen.findByTestId('notifications-bell'));
    const row = await screen.findByTestId('notifications-menu-row-n-1');
    await user.click(await within(row).findByText('Вам назначена заявка REQ-2026-000300'));

    await waitFor(() => expect(router.state.location.pathname).toBe('/requests/r-1'));
    await waitFor(() => expect(posted(fetchMock, '/notifications/n-1/read')).toHaveLength(1));
    await waitFor(() => expect(screen.queryByTestId('notifications-badge')).not.toBeInTheDocument());
  });

  it('системному администратору колокольчик не показывается и запросов нет', async () => {
    const { fetchMock } = api([], admin, 'ADMIN');
    renderApp('/admin/companies');

    expect(await screen.findByTestId('main-menu')).toBeInTheDocument();
    expect(screen.queryByTestId('notifications-bell')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/notifications'))).toBe(false);
  });

  it('если запрос счётчика упал, шапка работает, а счётчика нет', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('SALES_MANAGER');
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, manager),
      'GET /requests': () => jsonResponse(200, pageOf([])),
      'GET /notifications': () => jsonResponse(503, { code: 'SERVICE_UNAVAILABLE', retryable: true }),
    });
    renderApp('/requests');

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/notifications'))).toBe(true),
    );
    expect(await screen.findByTestId('notifications-bell')).toBeInTheDocument();
    expect(screen.queryByTestId('notifications-badge')).not.toBeInTheDocument();
  });
});

describe('UI-14. Экран уведомлений', () => {
  it('«Прочитано» отмечает одно уведомление и не уходит со страницы', async () => {
    const { fetchMock } = api([note({ id: 'n-1' }), note({ id: 'n-2', eventType: 'REQUEST_CLOSED' })]);
    const user = userEvent.setup();
    const { router } = renderApp('/notifications');

    const row = await screen.findByTestId('notifications-row-n-2');
    await user.click(within(row).getByTestId('action-read'));

    await waitFor(() => expect(screen.getByTestId('notifications-row-n-2')).toHaveAttribute('data-state', 'read'));
    expect(posted(fetchMock, '/notifications/n-2/read')).toHaveLength(1);
    expect(screen.getByTestId('notifications-row-n-1')).toHaveAttribute('data-state', 'unread');
    expect(router.state.location.pathname).toBe('/notifications');
  });

  it('«Отметить все прочитанными» снимает выделение и обнуляет счётчик', async () => {
    const { fetchMock } = api([note({ id: 'n-1' }), note({ id: 'n-2' })]);
    const user = userEvent.setup();
    renderApp('/notifications');

    expect(await screen.findByTestId('notifications-badge')).toHaveTextContent('2');
    const button = screen.getByTestId('action-read-all');
    await waitFor(() => expect(button).toBeEnabled());
    await user.click(button);

    await waitFor(() => expect(screen.getByTestId('notifications-row-n-1')).toHaveAttribute('data-state', 'read'));
    expect(screen.getByTestId('notifications-row-n-2')).toHaveAttribute('data-state', 'read');
    expect(posted(fetchMock, '/notifications/read-all')).toHaveLength(1);
    await waitFor(() => expect(screen.queryByTestId('notifications-badge')).not.toBeInTheDocument());
    expect(screen.getByTestId('action-read-all')).toBeDisabled();
  });

  it('пустой список: «Новых уведомлений нет», отмечать нечего', async () => {
    api([]);
    renderApp('/notifications');

    expect(await screen.findByText(t.empty)).toBeInTheDocument();
    expect(screen.getByTestId('action-read-all')).toBeDisabled();
  });

  it('при сбое показывает ошибку и кнопку «Обновить»', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('SALES_MANAGER');
    mockApi({
      'GET /me': () => jsonResponse(200, manager),
      'GET /notifications': () => jsonResponse(404, { code: 'NOT_FOUND', retryable: false }),
    });
    renderApp('/notifications');

    const error = await screen.findByTestId('state-error');
    expect(within(error).getByRole('button', { name: texts.common.refresh })).toBeInTheDocument();
  });

  it('если карточка недоступна, уведомление показывается без номера', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('SALES_MANAGER');
    mockApi({
      'GET /me': () => jsonResponse(200, manager),
      'GET /notifications': () => jsonResponse(200, pageOf([note({ requestId: 'r-hidden' })])),
      'GET /requests/r-hidden': () => jsonResponse(404, { code: 'NOT_FOUND', retryable: false }),
    });
    renderApp('/notifications');

    const row = await screen.findByTestId('notifications-row-n-1');
    expect(within(row).getByText('Вам назначена заявка')).toBeInTheDocument();
  });
});

describe('UI-14. Отказ в регистрации компании', () => {
  it('владелец видит причину в списке и по клику попадает на экран компании с причиной', async () => {
    const reason = 'ИНН не найден в справочнике';
    const owner: UserView = {
      id: 'u-owner',
      login: 'owner',
      fullName: 'Нина Ветрова',
      email: 'owner@example.test',
      companyRoles: ['CLIENT_OWNER'],
      companies: [{ id: 'c-1', name: 'ООО «Вектор»', status: 'REJECTED' }],
    };
    const rejected = note({
      requestId: undefined,
      eventType: 'COMPANY_REJECTED',
      payload: { companyId: 'c-1', reason },
    });
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('CLIENT_OWNER');
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, owner),
      'GET /notifications': () => jsonResponse(200, pageOf([rejected])),
      'POST /notifications/n-1/read': () => new Response(null, { status: 204 }),
      'GET /companies/c-1': () =>
        jsonResponse(200, {
          id: 'c-1',
          name: 'ООО «Вектор»',
          inn: '7701000000',
          status: 'REJECTED',
          rejectReason: reason,
          createdAt: '2026-11-20T09:00:00Z',
        }),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/notifications');

    const row = await screen.findByTestId('notifications-row-n-1');
    await user.click(within(row).getByText(`Компания не подтверждена. ${reason}`));

    await waitFor(() => expect(router.state.location.pathname).toBe('/company'));
    expect(await screen.findByTestId('company-page')).toHaveTextContent(reason);
    expect(posted(fetchMock, '/notifications/n-1/read')).toHaveLength(1);
  });
});

describe('Тексты и переходы уведомлений', () => {
  const operator = { requestNumber: 'REQ-1', client: false, coordinator: false };

  it('подпись зависит от получателя', () => {
    const assigned = note({ eventType: 'REQUEST_ASSIGNED' });
    expect(notificationText(assigned, operator)).toBe('Вам назначена заявка REQ-1');
    expect(notificationText(assigned, { ...operator, client: true })).toBe('Заявка REQ-1 передана менеджеру');
    const breach = note({ eventType: 'SLA_BREACH', payload: { stageType: 'TECH_CHECK' } });
    expect(notificationText(breach, operator)).toBe('Этап «Проверка ТВ» по заявке REQ-1 просрочен');
    expect(notificationText(breach, { ...operator, coordinator: true })).toContain('Переназначьте этап');
  });

  it('берёт этап, остаток и вид контрольного срока из payload', () => {
    const warning = note({ eventType: 'SLA_WARNING', payload: { stageType: 'TECH_CHECK', minutesLeft: 90 } });
    expect(notificationText(warning, operator)).toBe('Этап «Проверка ТВ» по заявке REQ-1: осталось 1 ч 30 мин');
    const deadline = note({ eventType: 'CONTROL_DEADLINE_BREACH', payload: { deadlineKind: 'MANAGER_ASSIGNMENT' } });
    expect(notificationText(deadline, operator)).toBe(
      'Заявка REQ-1: менеджер не назначен (срок истёк). Назначьте менеджера',
    );
  });

  it('отказ компании показывает причину, а без причины — общий текст', () => {
    const rejected = (reason?: string) =>
      note({ requestId: undefined, eventType: 'COMPANY_REJECTED', payload: { companyId: 'c-1', reason } });
    expect(notificationText(rejected('ИНН не найден'), operator)).toBe('Компания не подтверждена. ИНН не найден');
    expect(notificationText(rejected(), operator)).toBe('Компания не подтверждена');
    expect(notificationText(rejected('   '), operator)).toBe('Компания не подтверждена');
  });

  it('неизвестный код из ядра не ломает список', () => {
    const unknown = note({ eventType: 'NEW_EVENT' as NotificationView['eventType'] });
    expect(notificationText(unknown, operator)).toBe('Новое событие по заявке REQ-1');
  });

  it('ведёт в заявку, компанию или никуда', () => {
    expect(notificationTarget(note({}))).toBe('/requests/r-1');
    expect(notificationTarget(note({ requestId: undefined, eventType: 'COMPANY_CONFIRMED' }))).toBe('/company');
    expect(notificationTarget(note({ requestId: undefined, eventType: 'COMPANY_REJECTED' }))).toBe('/company');
    expect(notificationTarget(note({ requestId: undefined, eventType: 'EXPORT_READY' }))).toBeNull();
  });
});
