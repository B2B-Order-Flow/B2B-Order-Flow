import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { RequestView, Role, SlaView, StageView, UserView } from './api/types';
import { todayIso } from './lib/format';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Действия SC-01 в карточке, шаги 7–10: договор, монтаж, активация, акт (ui-requirements.md §7.3 и §8).

const t = texts.workflow;

const manager: UserView = {
  id: 'u-smirnova',
  login: 'a.smirnova',
  fullName: 'Анна Смирнова',
  email: 's@example.test',
  operatorRoles: ['SALES_MANAGER'],
};
const engineer: UserView = {
  id: 'u-kim',
  login: 'i.kim',
  fullName: 'Игорь Ким',
  email: 'k@example.test',
  operatorRoles: ['ENGINEER'],
};

const OFFER_ID = 'o-1';
const request = (patch: Partial<RequestView> = {}): RequestView => ({
  id: 'r-1',
  number: 'REQ-2026-000200',
  companyId: 'c-1',
  companyName: 'ООО «Вектор»',
  status: 'CONTRACT_SIGNING',
  version: 5,
  managerId: 'u-smirnova',
  createdAt: '2026-11-13T11:00:00Z',
  submittedAt: '2026-11-13T11:05:00Z',
  address: 'г. Санкт-Петербург, Невский пр., д. 1',
  requestedSpeedMbps: 100,
  selectedOfferId: OFFER_ID,
  offers: [{ id: OFFER_ID, status: 'SELECTED', tariffType: 'STANDARD', contractType: 'STANDARD' }],
  ...patch,
});
const offerRequest = (patch: Partial<RequestView> = {}) =>
  request({
    offers: [{ id: OFFER_ID, status: 'SELECTED', tariffType: 'STANDARD', contractType: 'OFFER' }],
    ...patch,
  });

const stage = (patch: Partial<StageView> = {}): StageView => ({
  id: 's-1',
  requestId: 'r-1',
  requestNumber: 'REQ-2026-000200',
  type: 'INSTALLATION',
  status: 'IN_PROGRESS',
  queueRole: 'ENGINEER',
  assigneeId: 'u-kim',
  version: 2,
  createdAt: '2026-11-14T10:00:00Z',
  takenAt: '2026-11-14T11:00:00Z',
  ...patch,
});

const page = <T,>(content: T[]) => ({
  content,
  page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 },
});
const signIn = (role: Role) => {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
};
const bodyOf = (init: RequestInit) => JSON.parse(String(init.body));

/** Заявка живёт в переменной: команда меняет её, следующий GET возвращает новую версию. */
function managerApi(initial: RequestView, extra: Parameters<typeof mockApi>[0] = {}) {
  const state = { current: initial };
  const command =
    (patch: Partial<RequestView>) =>
    (_: URL, init: RequestInit): Response => {
      state.current = { ...state.current, ...patch, version: bodyOf(init).version + 1 };
      return jsonResponse(200, state.current);
    };
  const fetchMock = mockApi({
    'GET /me': () => jsonResponse(200, manager),
    'GET /requests/r-1': () => jsonResponse(200, state.current),
    'GET /requests/r-1/history': () => jsonResponse(200, page([])),
    'POST /requests/r-1/contract/sent': command({ contractSentAt: '2026-11-20T09:00:00Z' }),
    'POST /requests/r-1/contract/concluded': command({ status: 'IN_EXECUTION', contractNumber: 'Д-77' }),
    'POST /requests/r-1/act/sent': command({ actSentAt: '2026-11-20T09:00:00Z' }),
    'POST /requests/r-1/act/signed': command({ status: 'CLOSED' }),
    'POST /requests/r-1/close-by-default': command({ status: 'CLOSED', acceptedByDefault: true }),
    ...extra,
  });
  return { fetchMock, state };
}

const callTo = (fetchMock: ReturnType<typeof mockApi>, suffix: string) =>
  fetchMock.mock.calls.find(([url, init]) => String(url).endsWith(suffix) && init?.method === 'POST');

const acceptance = (dueAt: string): SlaView => ({
  requestId: 'r-1',
  pauses: [],
  timers: [{ id: 't-1', kind: 'ACCEPTANCE', status: 'RUNNING', startedAt: '2026-11-20T09:00:00Z', dueAt }],
});

describe('Договор (шаг 7)', () => {
  it('менеджер отмечает отправку с version, кнопка исчезает, появляется подсказка об ожидании', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request());
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    await user.click(await screen.findByTestId('action-contract-sent'));

    await waitFor(() => expect(screen.queryByTestId('action-contract-sent')).not.toBeInTheDocument());
    expect(bodyOf(callTo(fetchMock, '/contract/sent')![1]!)).toEqual({ version: 5 });
    expect(screen.getByTestId('request-card-workflow-notice')).toHaveTextContent('Договор отправлен клиенту');
    expect(screen.getByTestId('action-contract-concluded')).toBeInTheDocument();
  });

  it('«Договор заключён» требует номер, не принимает будущую дату и передаёт номер, дату и version', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request());
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    await user.click(await screen.findByTestId('action-contract-concluded'));
    await user.click(await screen.findByTestId('action-contract-concluded-confirm'));
    expect(await screen.findByText(texts.common.required)).toBeInTheDocument();
    expect(callTo(fetchMock, '/contract/concluded')).toBeUndefined();

    await user.type(screen.getByTestId('contract-number'), 'Д-77');
    const date = screen.getByTestId('contract-date');
    await user.clear(date);
    await user.type(date, '2099-01-01');
    await user.click(screen.getByTestId('action-contract-concluded-confirm'));
    expect(await screen.findByText(t.contractDateFuture)).toBeInTheDocument();
    expect(callTo(fetchMock, '/contract/concluded')).toBeUndefined();

    await user.clear(date);
    await user.type(date, todayIso());
    await user.click(screen.getByTestId('action-contract-concluded-confirm'));

    await waitFor(() => expect(callTo(fetchMock, '/contract/concluded')).toBeTruthy());
    expect(bodyOf(callTo(fetchMock, '/contract/concluded')![1]!)).toEqual({
      contractNumber: 'Д-77',
      concludedAt: todayIso(),
      version: 5,
    });
  });

  it('при оферте у менеджера нет кнопок договора: ждём акцепт клиента', async () => {
    signIn('SALES_MANAGER');
    managerApi(offerRequest());
    renderApp('/requests/r-1');

    expect(await screen.findByTestId('request-card-workflow-notice')).toHaveTextContent(t.waitingOffer);
    expect(screen.queryByTestId('action-contract-sent')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-contract-concluded')).not.toBeInTheDocument();
  });

  it('менеджер чужой заявки кнопок договора не видит', async () => {
    signIn('SALES_MANAGER');
    managerApi(request({ managerId: 'u-other' }));
    renderApp('/requests/r-1');

    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('action-contract-sent')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-contract-concluded')).not.toBeInTheDocument();
  });
});

describe('Монтаж и активация (шаги 8–9)', () => {
  const engineerApi = (current: RequestView, extra: Parameters<typeof mockApi>[0] = {}) =>
    mockApi({
      'GET /me': () => jsonResponse(200, engineer),
      'GET /requests/r-1': () => jsonResponse(200, current),
      'GET /requests/r-1/history': () => jsonResponse(200, page([])),
      ...extra,
    });

  it('«Монтаж выполнен» находит этап в маршруте заявки, а не на странице «Моих задач»', async () => {
    signIn('ENGINEER');
    const installation = stage();
    const fetchMock = engineerApi(request({ status: 'IN_EXECUTION', stages: [installation] }), {
      // На первой странице «Моих задач» этого этапа нет
      'GET /stages/my': () => jsonResponse(200, page([stage({ id: 's-other', requestId: 'r-other' })])),
      'POST /stages/s-1/complete': () => jsonResponse(200, { ...installation, status: 'DONE' }),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    await user.click(await screen.findByTestId('action-complete'));

    await waitFor(() => expect(callTo(fetchMock, '/complete')).toBeTruthy());
    expect(bodyOf(callTo(fetchMock, '/complete')![1]!)).toEqual({ version: 2 });
  });

  it('этап в работе у другого инженера кнопок не даёт', async () => {
    signIn('ENGINEER');
    engineerApi(request({ status: 'IN_EXECUTION', stages: [stage({ assigneeId: 'u-other' })] }));
    renderApp('/requests/r-1');

    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('action-complete')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-claim')).not.toBeInTheDocument();
  });

  it('после «Взять» в очереди карточка сразу показывает действие, а не старую кнопку из кэша', async () => {
    signIn('ENGINEER');
    const queued = stage({ status: 'QUEUED', assigneeId: undefined, version: 0 });
    const taken = stage({ version: 1 });
    let current = request({ status: 'IN_EXECUTION', stages: [queued] });
    mockApi({
      'GET /me': () => jsonResponse(200, engineer),
      'GET /requests/r-1': () => jsonResponse(200, current),
      'GET /requests/r-1/history': () => jsonResponse(200, page([])),
      'GET /stages': () => jsonResponse(200, page([queued])),
      'POST /stages/s-1/claim': () => {
        current = { ...current, stages: [taken] };
        return jsonResponse(200, taken);
      },
    });
    const user = userEvent.setup();
    const { router } = renderApp('/requests/r-1');
    expect(await screen.findByTestId('action-claim')).toBeInTheDocument();

    await act(() => router.navigate('/queue'));
    const row = await screen.findByTestId('queue-row-REQ-2026-000200');
    await user.click(within(row).getByTestId('action-claim'));

    expect(await screen.findByTestId('action-complete')).toBeInTheDocument();
    expect(screen.queryByTestId('action-claim')).not.toBeInTheDocument();
  });

  it('следующий этап из очереди можно взять прямо в карточке', async () => {
    signIn('ENGINEER');
    const activation = stage({ id: 's-2', type: 'ACTIVATION', status: 'QUEUED', assigneeId: undefined, version: 0 });
    const fetchMock = engineerApi(request({ status: 'IN_EXECUTION', stages: [activation] }), {
      'POST /stages/s-2/claim': () => jsonResponse(200, { ...activation, status: 'IN_PROGRESS', version: 1 }),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    const claim = await screen.findByTestId('action-claim');
    expect(claim).toHaveTextContent(t.claimStage(texts.stageType.ACTIVATION));
    await user.click(claim);

    await waitFor(() => expect(callTo(fetchMock, '/stages/s-2/claim')).toBeTruthy());
    expect(bodyOf(callTo(fetchMock, '/stages/s-2/claim')![1]!)).toEqual({ version: 0 });
  });

  it('активация — только после подтверждения «Тест канала пройден?»', async () => {
    signIn('ENGINEER');
    const activation = stage({ id: 's-2', type: 'ACTIVATION', version: 1 });
    const fetchMock = engineerApi(request({ status: 'IN_EXECUTION', stages: [activation] }), {
      'POST /stages/s-2/activate': () => jsonResponse(200, { ...activation, status: 'DONE' }),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    await user.click(await screen.findByTestId('action-activate'));
    expect(await screen.findByText(t.activateText)).toBeInTheDocument();
    expect(callTo(fetchMock, '/activate')).toBeUndefined();

    await user.click(screen.getByTestId('action-activate-confirm'));
    await waitFor(() => expect(callTo(fetchMock, '/activate')).toBeTruthy());
    expect(bodyOf(callTo(fetchMock, '/activate')![1]!)).toEqual({ version: 1 });
  });
});

describe('Акт и закрытие (шаг 10)', () => {
  it('сначала «Акт отправлен», затем «Акт подписан» с новой версией', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request({ status: 'ACTIVATED' }), {
      'GET /requests/r-1/sla': () => jsonResponse(200, acceptance(new Date(Date.now() + 3_600_000).toISOString())),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    expect(screen.queryByTestId('action-act-signed')).not.toBeInTheDocument();
    await user.click(await screen.findByTestId('action-act-sent'));
    await user.click(await screen.findByTestId('action-act-signed'));

    await waitFor(() => expect(callTo(fetchMock, '/act/signed')).toBeTruthy());
    expect(bodyOf(callTo(fetchMock, '/act/sent')![1]!)).toEqual({ version: 5 });
    expect(bodyOf(callTo(fetchMock, '/act/signed')![1]!)).toEqual({ version: 6 });
  });

  it('до конца срока приёмки «Закрыть по умолчанию» недоступна', async () => {
    signIn('SALES_MANAGER');
    managerApi(request({ status: 'ACTIVATED', actSentAt: '2026-11-20T09:00:00Z' }), {
      'GET /requests/r-1/sla': () => jsonResponse(200, acceptance(new Date(Date.now() + 3_600_000).toISOString())),
    });
    renderApp('/requests/r-1');

    await waitFor(() => expect(screen.getByTestId('action-close-by-default')).toBeDisabled());
  });

  it('после срока приёмки закрывает заявку', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request({ status: 'ACTIVATED', actSentAt: '2026-11-20T09:00:00Z' }), {
      'GET /requests/r-1/sla': () => jsonResponse(200, acceptance(new Date(Date.now() - 60_000).toISOString())),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    const close = await screen.findByTestId('action-close-by-default');
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/sla'))).toBe(true));
    await waitFor(() => expect(close).toBeEnabled());
    await user.click(close);

    await waitFor(() => expect(callTo(fetchMock, '/close-by-default')).toBeTruthy());
  });

  it('пока срок приёмки загружается, закрыть нельзя, а «Акт подписан» доступен', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request({ status: 'ACTIVATED', actSentAt: '2026-11-20T09:00:00Z' }), {
      'GET /requests/r-1/sla': () => new Promise<Response>(() => undefined),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    expect(await screen.findByTestId('action-close-by-default')).toBeDisabled();
    await user.click(screen.getByTestId('action-act-signed'));

    await waitFor(() => expect(callTo(fetchMock, '/act/signed')).toBeTruthy());
    expect(callTo(fetchMock, '/close-by-default')).toBeUndefined();
  });

  it('если ядро не отдало срок приёмки, закрыть нельзя', async () => {
    signIn('SALES_MANAGER');
    const { fetchMock } = managerApi(request({ status: 'ACTIVATED', actSentAt: '2026-11-20T09:00:00Z' }), {
      'GET /requests/r-1/sla': () => jsonResponse(404, { code: 'NOT_FOUND', retryable: false }),
    });
    renderApp('/requests/r-1');

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/sla'))).toBe(true));
    expect(await screen.findByTestId('action-close-by-default')).toBeDisabled();
    expect(screen.getByTestId('action-act-signed')).toBeEnabled();
  });

  it('если часы разошлись и ядро ответило 409, показывает понятный текст', async () => {
    signIn('SALES_MANAGER');
    managerApi(request({ status: 'ACTIVATED', actSentAt: '2026-11-20T09:00:00Z' }), {
      'GET /requests/r-1/sla': () => jsonResponse(200, acceptance(new Date(Date.now() - 60_000).toISOString())),
      'POST /requests/r-1/close-by-default': () => jsonResponse(409, { code: 'ILLEGAL_TRANSITION', retryable: false }),
    });
    const user = userEvent.setup();
    renderApp('/requests/r-1');

    const close = await screen.findByTestId('action-close-by-default');
    await waitFor(() => expect(close).toBeEnabled());
    await user.click(close);

    expect(await screen.findByText(t.acceptanceNotExpired)).toBeInTheDocument();
    expect(screen.queryByText(texts.errors.ILLEGAL_TRANSITION!)).not.toBeInTheDocument();
  });
});
