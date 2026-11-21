import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { RequestView, SlaView, StageView, UserView } from './api/types';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Экраны SC-00 и SC-01 по шагам контрольной точки (plan-week2.md §9.2–9.3).

const sokolov: UserView = {
  id: 'u-sokolov',
  login: 'a.sokolov',
  fullName: 'Алексей Соколов',
  email: 'a.sokolov@example.test',
  companyRoles: ['CLIENT_OWNER'],
  operatorRoles: [],
  companies: [{ id: 'c-sokolov', name: 'ИП Соколов А. В.', status: 'ACTIVE' }],
};
const nesterov: UserView = {
  id: 'u-nesterov',
  login: 'p.nesterov',
  fullName: 'Пётр Нестеров',
  email: 'p@example.test',
  companies: [],
};
const orlov: UserView = {
  id: 'u-orlov',
  login: 'p.orlov',
  fullName: 'Павел Орлов',
  email: 'o@example.test',
  operatorRoles: ['TECH_SPECIALIST'],
};
const smirnova: UserView = {
  id: 'u-smirnova',
  login: 'a.smirnova',
  fullName: 'Анна Смирнова',
  email: 's@example.test',
  operatorRoles: ['SALES_MANAGER'],
};

const request = (patch: Partial<RequestView> = {}): RequestView => ({
  id: 'r-131',
  number: 'REQ-2026-000131',
  companyId: 'c-sokolov',
  companyName: 'ИП Соколов А. В.',
  status: 'WITH_MANAGER',
  version: 3,
  managerId: 'u-smirnova',
  createdAt: '2026-11-13T11:00:00Z',
  submittedAt: '2026-11-13T11:05:00Z',
  address: 'Кофейня, ул. Садовая, д. 12',
  requestedSpeedMbps: 100,
  desiredDate: '2026-12-15',
  contractPreference: 'OFFER',
  ...patch,
});

const stage = (patch: Partial<StageView> = {}): StageView => ({
  id: 's-131',
  requestId: 'r-131',
  requestNumber: 'REQ-2026-000131',
  type: 'TECH_CHECK',
  status: 'QUEUED',
  queueRole: 'TECH_SPECIALIST',
  version: 0,
  createdAt: '2026-11-13T11:30:00Z',
  ...patch,
});

const slaView: SlaView = {
  requestId: 'r-131',
  timers: [
    {
      id: 't-1',
      kind: 'STAGE_SLA',
      stageId: 's-131',
      stageType: 'TECH_CHECK',
      status: 'RUNNING',
      normMinutes: 1440,
      startedAt: '2026-11-13T11:30:00Z',
      elapsedSlaSeconds: 7200,
      remainingSlaSeconds: 1320 * 60,
      calendarElapsedSeconds: 72 * 60,
      overdue: false,
    },
  ],
  pauses: [],
};

const page = <T,>(content: T[]) => ({
  content,
  page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 },
});
const signIn = (role: string | null) => {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role as never);
};
const bodyOf = (init: RequestInit) => JSON.parse(String(init.body));

describe('сессия', () => {
  it('после перезагрузки страницы первый /me уходит с токеном и сессия сохраняется', async () => {
    signIn('CLIENT_OWNER');
    mockApi({
      'GET /me': (_, init) =>
        new Headers(init.headers).get('Authorization') === 'Bearer jwt'
          ? jsonResponse(200, sokolov)
          : jsonResponse(401, { code: 'UNAUTHORIZED', retryable: false }),
      'GET /requests': () => jsonResponse(200, page([request()])),
    });
    const { router } = renderApp('/requests');
    expect(await screen.findByTestId('requests-table')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/requests');
    expect(sessionStore.getToken()).toBe('jwt');
  });
});

describe('UI-03. Регистрация компании', () => {
  it('ИНН не из справочника: плашка и ошибка под полем ИНН, компания не создана', async () => {
    signIn(null);
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, nesterov),
      'POST /companies': () => jsonResponse(422, { code: 'COMPANY_INN_NOT_FOUND', retryable: false }),
    });
    const user = userEvent.setup();
    renderApp('/company/new');

    await user.type(await screen.findByTestId('company-new-name'), 'ООО «Фантом»');
    await user.type(screen.getByTestId('company-new-inn'), '9977004455');
    await user.type(screen.getByTestId('company-new-legal-address'), 'г. Москва, ул. Примерная, д. 1');
    await user.click(screen.getByTestId('company-new-submit'));

    expect(await screen.findByTestId('company-new-error')).toHaveTextContent(texts.errors.COMPANY_INN_NOT_FOUND!);
    expect(screen.getByText(texts.companyNew.errors.innNotFound)).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(new Headers(post![1]!.headers).get('Idempotency-Key')).toBeTruthy();
  });

  it('неверная контрольная сумма ИНН ловится до отправки', async () => {
    signIn(null);
    const fetchMock = mockApi({ 'GET /me': () => jsonResponse(200, nesterov) });
    const user = userEvent.setup();
    renderApp('/company/new');

    await user.type(await screen.findByTestId('company-new-name'), 'ООО «Опечатка»');
    await user.type(screen.getByTestId('company-new-inn'), '9977004456');
    await user.type(screen.getByTestId('company-new-legal-address'), 'г. Москва');
    await user.click(screen.getByTestId('company-new-submit'));

    expect(await screen.findByText(texts.companyNew.errors.inn)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });
});

describe('UI-05 и UI-07. Заявки клиента', () => {
  it('клиент видит подсказку «Примите условия оферты» у заявки на подписании', async () => {
    signIn('CLIENT_OWNER');
    mockApi({
      'GET /me': () => jsonResponse(200, sokolov),
      'GET /requests': () => jsonResponse(200, page([request({ status: 'CONTRACT_SIGNING' })])),
    });
    renderApp('/requests');
    expect(await screen.findByTestId('request-action-hint')).toHaveTextContent(texts.requests.hintAcceptOffer);
  });

  it('«Отправить оператору» сохраняет черновик и отправляет его с версией, затем открывает карточку', async () => {
    signIn('CLIENT_OWNER');
    const draft = request({ id: 'r-new', status: 'DRAFT', version: 0, managerId: undefined });
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, sokolov),
      'POST /requests': (_, init) => jsonResponse(201, { ...draft, ...bodyOf(init) }),
      'POST /requests/r-new/submit': () => jsonResponse(200, { ...draft, status: 'WITH_MANAGER', version: 1 }),
      'GET /requests/r-new': () => jsonResponse(200, { ...draft, status: 'WITH_MANAGER', version: 1 }),
      'GET /requests/r-new/history': () => jsonResponse(200, page([])),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/requests/new');

    await user.type(await screen.findByTestId('request-form-address'), 'г. Москва, ул. Садовая, д. 12');
    await user.type(screen.getByTestId('request-form-speed'), '100');
    await user.type(screen.getByTestId('request-form-date'), '2099-12-15');
    await user.type(screen.getByTestId('request-form-contact-phone'), '+7 (900) 000-00-00');
    await user.click(screen.getByTestId('request-form-submit'));

    await waitFor(() => expect(router.state.location.pathname).toBe('/requests/r-new'));
    const create = fetchMock.mock.calls.find(
      ([url, init]) => String(url).endsWith('/requests') && init?.method === 'POST',
    );
    expect(new Headers(create![1]!.headers).get('Idempotency-Key')).toBeTruthy();
    expect(bodyOf(create![1]!)).toMatchObject({ requestedSpeedMbps: 100, contactName: 'Алексей Соколов' });
    const submit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/submit'));
    expect(bodyOf(submit![1]!)).toEqual({ version: 0 });
  });
});

describe('UI-08. Действия SC-01', () => {
  it('менеджер фиксирует коммерческие условия: индивидуальная цена требует скидку', async () => {
    signIn('SALES_MANAGER');
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, smirnova),
      'GET /requests/r-131': () => jsonResponse(200, request()),
      'POST /requests/r-131/commercial-terms': () => jsonResponse(200, request({ status: 'TECH_CHECK', version: 4 })),
      'GET /requests/r-131/history': () => jsonResponse(200, page([])),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-131');

    await user.click(await screen.findByTestId('terms-tariff-INDIVIDUAL'));
    await user.click(screen.getByTestId('action-commercial-terms'));
    expect(await screen.findByText(texts.common.required)).toBeInTheDocument();

    await user.type(screen.getByTestId('terms-discount'), '10,5');
    await user.click(screen.getByTestId('action-commercial-terms'));
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/commercial-terms'))).toBe(true),
    );
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/commercial-terms'));
    expect(bodyOf(call![1]!)).toEqual({
      tariffType: 'INDIVIDUAL',
      contractType: 'OFFER',
      discountPercent: '10.50',
      version: 3,
    });
  });

  it('техспециалист: «Нет» сначала спрашивает подтверждение, затем отправляет причину', async () => {
    signIn('TECH_SPECIALIST');
    const taken = stage({ status: 'IN_PROGRESS', assigneeId: 'u-orlov', version: 1 });
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, orlov),
      'GET /requests/r-131': () => jsonResponse(200, request({ status: 'TECH_CHECK' })),
      'GET /stages/my': () => jsonResponse(200, page([taken])),
      'POST /stages/s-131/tech-result': () => jsonResponse(200, { ...taken, status: 'DONE' }),
      'GET /requests/r-131/history': () => jsonResponse(200, page([])),
      'GET /requests/r-131/sla': () => jsonResponse(200, slaView),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-131');

    // Остаток и календарное время — из ответа ядра
    expect(await screen.findByTestId('sla-indicator-value')).toHaveTextContent('осталось 2 р.д. 6 ч');
    expect(screen.getByText(/Календарное: 1 ч 12 мин/)).toBeInTheDocument();

    await user.click(await screen.findByTestId('tech-result-option-NONE'));
    await user.selectOptions(screen.getByTestId('tech-reason'), 'OTHER');
    await user.type(screen.getByTestId('tech-comment'), 'Нет кабельной канализации');
    await user.click(screen.getByTestId('action-tech-result'));

    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/tech-result'))).toBe(false);
    await user.click(await screen.findByTestId('action-tech-result-confirm'));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/tech-result'))).toBe(true));
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/tech-result'));
    expect(bodyOf(call![1]!)).toEqual({
      result: 'NONE',
      reasonCode: 'OTHER',
      comment: 'Нет кабельной канализации',
      version: 1,
    });
  });
});

describe('UI-08. Результат ТВ «со стройкой»', () => {
  it('до недели 3 ядро отвечает 409 — форма показывает понятный текст', async () => {
    signIn('TECH_SPECIALIST');
    const taken = stage({ status: 'IN_PROGRESS', assigneeId: 'u-orlov', version: 1 });
    mockApi({
      'GET /me': () => jsonResponse(200, orlov),
      'GET /requests/r-131': () => jsonResponse(200, request({ status: 'TECH_CHECK' })),
      'GET /stages/my': () => jsonResponse(200, page([taken])),
      'GET /requests/r-131/history': () => jsonResponse(200, page([])),
      'GET /requests/r-131/sla': () => jsonResponse(200, slaView),
      'POST /stages/s-131/tech-result': () => jsonResponse(409, { code: 'ILLEGAL_TRANSITION', retryable: false }),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-131');

    await user.click(await screen.findByTestId('tech-result-option-WITH_CONSTRUCTION'));
    await user.type(screen.getByTestId('tech-capex'), '350000');
    await user.type(screen.getByTestId('tech-days'), '20');
    await user.click(screen.getByTestId('action-tech-result'));

    expect(await screen.findByTestId('tech-construction-unavailable')).toHaveTextContent(
      texts.techResult.constructionUnavailableTitle,
    );
    expect(screen.queryByText(texts.errors.ILLEGAL_TRANSITION!)).not.toBeInTheDocument();
    expect(screen.queryByTestId('tech-return')).not.toBeInTheDocument();
  });
});

describe('UI-12. Мои задачи', () => {
  it('показывает только этапы в работе и в порядке ядра (по остатку SLA)', async () => {
    signIn('TECH_SPECIALIST');
    mockApi({
      'GET /me': () => jsonResponse(200, orlov),
      'GET /stages/my': () =>
        jsonResponse(
          200,
          page([
            stage({
              id: 's-2',
              requestNumber: 'REQ-2026-000140',
              status: 'IN_PROGRESS',
              createdAt: '2026-11-15T10:00:00Z',
            }),
            stage({ id: 's-3', requestNumber: 'REQ-2026-000141', status: 'QUEUED', assigneeId: 'u-orlov' }),
            stage({
              id: 's-1',
              requestNumber: 'REQ-2026-000139',
              status: 'IN_PROGRESS',
              createdAt: '2026-11-10T10:00:00Z',
            }),
          ]),
        ),
    });
    renderApp('/my-tasks');

    const rows = await screen.findAllByTestId(/^my-tasks-row-/);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByText('REQ-2026-000140')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('REQ-2026-000139')).toBeInTheDocument();
    expect(screen.queryByText('REQ-2026-000141')).not.toBeInTheDocument();
  });
});

describe('UI-11. Очередь роли', () => {
  it('сверху этапы, которые ждут дольше; срок — «—»; «Взять» открывает карточку заявки', async () => {
    signIn('TECH_SPECIALIST');
    const late = stage({
      id: 's-125',
      requestId: 'r-125',
      requestNumber: 'REQ-2026-000125',
      createdAt: '2026-11-11T06:10:00Z',
      requiredByRules: ['R-02'],
    });
    const fetchMock = mockApi({
      'GET /me': () => jsonResponse(200, orlov),
      'GET /stages': () => jsonResponse(200, page([stage(), late])),
      'POST /stages/s-131/claim': () => jsonResponse(200, stage({ status: 'IN_PROGRESS', version: 1 })),
      'GET /requests/r-131': () => jsonResponse(200, request({ status: 'TECH_CHECK' })),
      'GET /stages/my': () => jsonResponse(200, page([])),
      'GET /requests/r-131/history': () => jsonResponse(200, page([])),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/queue');

    const rows = await screen.findAllByTestId(/^queue-row-/);
    // Строка находится по номеру заявки (ui-requirements.md §11.3)
    expect(rows[0]).toBe(screen.getByTestId('queue-row-REQ-2026-000125'));
    expect(within(rows[0]!).getByText('REQ-2026-000125')).toBeInTheDocument();
    expect(within(rows[0]!).getByText('по правилам R-02')).toBeInTheDocument();
    expect(within(rows[0]!).getByText('—')).toBeInTheDocument();
    const queueCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/stages?'));
    expect(String(queueCall![0])).toContain('queueRole=TECH_SPECIALIST');

    await user.click(within(rows[1]!).getByTestId('action-claim'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/requests/r-131'));
  });
});
