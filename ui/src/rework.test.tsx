import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { RequestRevisionView, RequestView, Role, StageView, UserView } from './api/types';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Доработка заявки недели 3: возврат с ТВ (T09b), правка данных и повторная отправка (T10), ревизии.

const t = texts.rework;

const person = (id: string, roles: Role[]): UserView => ({
  id,
  login: id,
  fullName: 'Анна Смирнова',
  email: `${id}@example.test`,
  operatorRoles: roles as UserView['operatorRoles'],
});
const manager = person('u-smirnova', ['SALES_MANAGER']);
const orlov = { ...person('u-orlov', ['TECH_SPECIALIST']), fullName: 'Павел Орлов' };

const techStage = (patch: Partial<StageView> = {}): StageView => ({
  id: 's-tech',
  requestId: 'r-1',
  requestNumber: 'REQ-2026-000150',
  type: 'TECH_CHECK',
  status: 'IN_PROGRESS',
  queueRole: 'TECH_SPECIALIST',
  assigneeId: 'u-orlov',
  version: 2,
  createdAt: '2026-11-26T09:00:00Z',
  ...patch,
});

const request = (patch: Partial<RequestView> = {}): RequestView => ({
  id: 'r-1',
  number: 'REQ-2026-000150',
  companyId: 'c-1',
  companyName: 'ООО «Логистик Плюс»',
  status: 'REWORK',
  version: 8,
  managerId: 'u-smirnova',
  createdAt: '2026-11-20T09:00:00Z',
  address: 'склад, ул. Складская, д. 5',
  requestedSpeedMbps: 500,
  desiredDate: '2099-12-15',
  contactName: 'Наталья Фёдорова',
  contactPhone: '+7 (900) 000-00-01',
  selectedOfferId: 'o-1',
  offers: [
    {
      id: 'o-1',
      status: 'SELECTED',
      tariffType: 'INDIVIDUAL',
      discountPercent: '15.00',
      contractType: 'CLIENT_FORM',
      speedMbps: 500,
    },
  ],
  stages: [techStage({ status: 'RETURNED' })],
  ...patch,
});

const page = <T,>(content: T[]) => ({
  content,
  page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 },
});
const returnEntry = {
  id: 'h-1',
  createdAt: '2026-11-26T10:00:00Z',
  actorId: 'u-orlov',
  actorRole: 'TECH_SPECIALIST' as const,
  action: 'STAGE_RETURNED',
  fromStatus: 'TECH_CHECK',
  toStatus: 'REWORK',
  comment: 'Уточните адрес: в справочнике нет дома 5',
};
const bodyOf = (init: RequestInit) => JSON.parse(String(init.body));

function open(role: Role, user: UserView, view: RequestView, extra: Parameters<typeof mockApi>[0] = {}) {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
  const fetchMock = mockApi({
    'GET /me': () => jsonResponse(200, user),
    'GET /requests/r-1': () => jsonResponse(200, view),
    'GET /requests/r-1/history': () => jsonResponse(200, page([returnEntry])),
    'GET /requests/r-1/sla': () => jsonResponse(200, { requestId: 'r-1', timers: [], pauses: [] }),
    'GET /requests/r-1/revisions': () => jsonResponse(200, page([])),
    ...extra,
  });
  renderApp('/requests/r-1');
  return fetchMock;
}

const calls = (fetchMock: ReturnType<typeof mockApi>, method: string, suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && init?.method === method);

describe('Возврат с проверки ТВ (T09b)', () => {
  it('техспециалист возвращает заявку с обязательным комментарием', async () => {
    const view = request({ status: 'TECH_CHECK', stages: [techStage()] });
    const fetchMock = open('TECH_SPECIALIST', orlov, view, {
      'POST /stages/s-tech/return': () => jsonResponse(200, techStage({ status: 'RETURNED', version: 3 })),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByTestId('action-tech-return'));
    await user.click(await screen.findByTestId('action-tech-return-confirm'));
    expect(await screen.findByText(texts.common.required)).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/return')).toHaveLength(0);

    await user.type(screen.getByTestId('tech-return-comment'), 'Уточните адрес: в справочнике нет дома 5');
    await user.click(screen.getByTestId('action-tech-return-confirm'));
    await waitFor(() => expect(calls(fetchMock, 'POST', '/return')).toHaveLength(1));
    expect(bodyOf(calls(fetchMock, 'POST', '/return')[0]![1]!)).toEqual({
      comment: 'Уточните адрес: в справочнике нет дома 5',
      version: 2,
    });
    expect(await screen.findByText(t.returned)).toBeInTheDocument();
  });

  it('чужой этап ТВ вернуть нельзя', async () => {
    open('TECH_SPECIALIST', orlov, request({ status: 'TECH_CHECK', stages: [techStage({ assigneeId: 'u-other' })] }));
    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('action-tech-return')).not.toBeInTheDocument();
  });
});

describe('Доработка у менеджера', () => {
  it('показывает, что исправить, и заполняет форму данными заявки и условий', async () => {
    open('SALES_MANAGER', manager, request());

    expect(await screen.findByTestId('rework-reason')).toHaveTextContent('Уточните адрес: в справочнике нет дома 5');
    const form = screen.getByTestId('rework-form');
    expect(within(form).getByTestId('rework-address')).toHaveValue('склад, ул. Складская, д. 5');
    expect(within(form).getByTestId('rework-speed')).toHaveValue('500');
    expect(within(form).getByTestId('rework-discount')).toHaveValue('15,00');
    expect(within(form).getByTestId('action-save-data')).toBeDisabled();
  });

  it('«Сохранить» отправляет данные и условия с версией заявки', async () => {
    const fetchMock = open('SALES_MANAGER', manager, request(), {
      'PATCH /requests/r-1/data': (_, init) => jsonResponse(200, request({ version: bodyOf(init).version + 1 })),
    });
    const user = userEvent.setup();

    const address = await screen.findByTestId('rework-address');
    await user.clear(address);
    await user.type(address, 'склад, ул. Складская, д. 5, стр. 2');
    await user.click(screen.getByTestId('rework-tariff-STANDARD'));
    await user.click(screen.getByTestId('action-save-data'));

    await waitFor(() => expect(calls(fetchMock, 'PATCH', '/data')).toHaveLength(1));
    expect(bodyOf(calls(fetchMock, 'PATCH', '/data')[0]![1]!)).toEqual({
      address: 'склад, ул. Складская, д. 5, стр. 2',
      desiredDate: '2099-12-15',
      contactName: 'Наталья Фёдорова',
      contactPhone: '+7 (900) 000-00-01',
      speedMbps: 500,
      tariffType: 'STANDARD',
      contractType: 'CLIENT_FORM',
      version: 8,
    });
    expect(calls(fetchMock, 'POST', '/resubmit')).toHaveLength(0);
  });

  it('«Отправить повторно» сначала сохраняет изменения, затем отправляет с новой версией', async () => {
    const fetchMock = open('SALES_MANAGER', manager, request(), {
      'PATCH /requests/r-1/data': () => jsonResponse(200, request({ version: 9 })),
      'POST /requests/r-1/resubmit': () => jsonResponse(200, request({ status: 'TECH_CHECK', version: 10 })),
    });
    const user = userEvent.setup();

    const speed = await screen.findByTestId('rework-speed');
    await user.clear(speed);
    await user.type(speed, '300');
    await user.click(screen.getByTestId('action-resubmit'));

    await waitFor(() => expect(calls(fetchMock, 'POST', '/resubmit')).toHaveLength(1));
    expect(bodyOf(calls(fetchMock, 'PATCH', '/data')[0]![1]!)).toMatchObject({ speedMbps: 300, version: 8 });
    expect(bodyOf(calls(fetchMock, 'POST', '/resubmit')[0]![1]!)).toEqual({ version: 9 });
    expect(await screen.findByText(t.resubmitted)).toBeInTheDocument();
  });

  it('без изменений «Отправить повторно» не сохраняет, а сразу отправляет', async () => {
    const fetchMock = open('SALES_MANAGER', manager, request(), {
      'POST /requests/r-1/resubmit': () => jsonResponse(200, request({ status: 'TECH_CHECK', version: 9 })),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByTestId('action-resubmit'));
    await waitFor(() => expect(calls(fetchMock, 'POST', '/resubmit')).toHaveLength(1));
    expect(calls(fetchMock, 'PATCH', '/data')).toHaveLength(0);
    expect(bodyOf(calls(fetchMock, 'POST', '/resubmit')[0]![1]!)).toEqual({ version: 8 });
  });

  it('проверки полей: индивидуальная цена требует скидку, адрес обязателен', async () => {
    const fetchMock = open('SALES_MANAGER', manager, request());
    const user = userEvent.setup();

    await user.clear(await screen.findByTestId('rework-address'));
    await user.clear(screen.getByTestId('rework-discount'));
    await user.click(screen.getByTestId('action-resubmit'));

    expect(await screen.findAllByText(texts.common.required)).toHaveLength(2);
    expect(calls(fetchMock, 'PATCH', '/data')).toHaveLength(0);
    expect(calls(fetchMock, 'POST', '/resubmit')).toHaveLength(0);
  });

  it('форма доработки только у менеджера этой заявки', async () => {
    open('SALES_MANAGER', manager, request({ managerId: 'u-other' }));
    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('rework-form')).not.toBeInTheDocument();
  });
});

describe('Что изменилось между ревизиями', () => {
  const revisions: RequestRevisionView[] = [
    {
      id: 'rev-2',
      revisionNo: 2,
      reason: 'REWORK',
      createdAt: '2026-11-26T12:00:00Z',
      changes: [
        { field: 'tariffType', previousValue: 'INDIVIDUAL', newValue: 'STANDARD' },
        { field: 'discountPercent', previousValue: '15.00' },
      ],
    },
    {
      id: 'rev-1',
      revisionNo: 1,
      reason: 'SUBMIT',
      createdAt: '2026-11-20T09:00:00Z',
      changes: [{ field: 'address', newValue: 'склад' }],
    },
  ];

  it('менеджер видит изменённые поля «было → стало», первая ревизия не показывается', async () => {
    open('SALES_MANAGER', manager, request({ status: 'IN_APPROVAL', stages: [] }), {
      'GET /requests/r-1/revisions': () => jsonResponse(200, page(revisions)),
    });

    const panel = await screen.findByTestId('request-card-revisions');
    expect(within(panel).getByTestId('revision-2')).toHaveTextContent(t.revisionTitle(2, t.reasons.REWORK!));
    expect(within(panel).queryByTestId('revision-1')).not.toBeInTheDocument();
    const changes = within(panel).getAllByTestId('revision-change');
    expect(changes[0]).toHaveTextContent('Тариф:Индивидуальная цена→Типовой');
    expect(changes[1]).toHaveTextContent('Скидка:15,00 %→—');
  });

  it('другим ролям ревизии не запрашиваются', async () => {
    const fetchMock = open('LAWYER', person('u-volkova', ['LAWYER']), request({ status: 'IN_APPROVAL' }));
    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/revisions'))).toBe(false);
  });

  it('если ядро не отдало ревизии, блока нет, карточка работает', async () => {
    open('COORDINATOR', person('u-kovaleva', ['COORDINATOR']), request({ status: 'IN_APPROVAL' }), {
      'GET /requests/r-1/revisions': () => jsonResponse(403, { code: 'ACCESS_DENIED', retryable: false }),
    });
    expect(await screen.findByTestId('request-card-history')).toBeInTheDocument();
    expect(screen.queryByTestId('request-card-revisions')).not.toBeInTheDocument();
  });
});
