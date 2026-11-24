import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { RequestView, Role, SlaTimerView, SlaView, UserView } from './api/types';
import { slaSnapshot } from './lib/sla';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Блок «Сроки» в карточке заявки (sla.md §10, план недели 2, раздел 4, п. 2).

const t = texts.sla;

const userWith = (roles: Partial<UserView>): UserView => ({
  id: 'u-1',
  login: 'user',
  fullName: 'Анна Смирнова',
  email: 'u@example.test',
  ...roles,
});

const request: RequestView = {
  id: 'r-1',
  number: 'REQ-2026-000300',
  companyId: 'c-1',
  status: 'CONTRACT_SIGNING',
  version: 3,
  managerId: 'u-1',
  createdAt: '2026-11-13T11:00:00Z',
  address: 'г. Москва, ул. Лесная, д. 3',
};

const timer = (patch: Partial<SlaTimerView>): SlaTimerView => ({
  id: 't-1',
  kind: 'STAGE_SLA',
  stageType: 'TECH_CHECK',
  status: 'RUNNING',
  normMinutes: 1440,
  startedAt: '2026-11-13T11:00:00Z',
  elapsedSlaSeconds: 120 * 60,
  remainingSlaSeconds: 1320 * 60,
  calendarElapsedSeconds: 72 * 60,
  overdue: false,
  ...patch,
});

const sla: SlaView = {
  requestId: 'r-1',
  timers: [
    timer({ id: 't-done', stageType: 'TECH_CHECK', status: 'STOPPED' }),
    timer({
      id: 't-paused',
      stageType: 'CONTRACT_SIGNING',
      status: 'PAUSED',
      pause: { id: 'p-1', reasonCode: 'AWAIT_CLIENT_SIGNATURE', startedAt: '2026-11-20T09:00:00Z' },
    }),
    timer({ id: 't-control', kind: 'TECH_RESULT', stageType: undefined, normMinutes: 43200 }),
  ],
  pauses: [
    {
      id: 'p-1',
      reasonCode: 'AWAIT_CLIENT_SIGNATURE',
      comment: 'Ждём подпись директора',
      startedAt: '2026-11-20T09:00:00Z',
      limitAt: '2026-12-04T09:00:00Z',
    },
  ],
};

const empty = { content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } };

function open(role: Role, user: UserView, slaResponse: () => Response) {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
  const fetchMock = mockApi({
    'GET /me': () => jsonResponse(200, user),
    'GET /requests/r-1': () => jsonResponse(200, request),
    'GET /requests/r-1/history': () => jsonResponse(200, empty),
    'GET /requests/r-1/sla': slaResponse,
  });
  renderApp('/requests/r-1');
  return fetchMock;
}

describe('Блок «Сроки» в карточке', () => {
  it('показывает сроки этапов, контрольные сроки, два времени и журнал пауз', async () => {
    open('SALES_MANAGER', userWith({ operatorRoles: ['SALES_MANAGER'] }), () => jsonResponse(200, sla));

    const panel = await screen.findByTestId('request-card-sla');
    expect(within(panel).getByText(t.stages)).toBeInTheDocument();
    expect(within(panel).getByText(t.control)).toBeInTheDocument();
    expect(within(panel).getByTestId('request-card-sla-timer-t-paused-value')).toHaveAttribute('data-state', 'paused');
    expect(within(panel).getByTestId('request-card-sla-timer-t-done-value')).toHaveTextContent('завершён');
    expect(within(panel).getByTestId('request-card-sla-timer-t-control-value')).toHaveTextContent('осталось');
    expect(within(panel).getAllByText(/Календарное: 1 ч 12 мин/).length).toBeGreaterThan(0);

    const pause = within(panel).getByTestId('sla-pause');
    expect(pause).toHaveTextContent(texts.pauseReason.AWAIT_CLIENT_SIGNATURE);
    expect(pause).toHaveTextContent('Ждём подпись директора');
  });

  it('у строки срока — ID таймера в метке, вид и этап в атрибутах', async () => {
    open('SALES_MANAGER', userWith({ operatorRoles: ['SALES_MANAGER'] }), () => jsonResponse(200, sla));

    const panel = await screen.findByTestId('request-card-sla');
    const paused = within(panel).getByTestId('request-card-sla-timer-t-paused');
    expect(paused).toHaveAttribute('data-kind', 'STAGE_SLA');
    expect(paused).toHaveAttribute('data-stage', 'CONTRACT_SIGNING');
    const control = within(panel).getByTestId('request-card-sla-timer-t-control');
    expect(control).toHaveAttribute('data-kind', 'TECH_RESULT');
    expect(control).not.toHaveAttribute('data-stage');
  });

  it('два таймера одного вида различаются по метке', async () => {
    const twice: SlaView = {
      requestId: 'r-1',
      timers: [
        timer({ id: 't-old', kind: 'MANAGER_ASSIGNMENT', stageType: undefined, status: 'STOPPED' }),
        timer({ id: 't-new', kind: 'MANAGER_ASSIGNMENT', stageType: undefined, status: 'RUNNING' }),
      ],
      pauses: [],
    };
    open('COORDINATOR', userWith({ operatorRoles: ['COORDINATOR'] }), () => jsonResponse(200, twice));

    const panel = await screen.findByTestId('request-card-sla');
    const rows = within(panel)
      .getAllByTestId(/^request-card-sla-timer-[^-]+-[^-]+$/)
      .filter((el) => el.getAttribute('data-kind') === 'MANAGER_ASSIGNMENT');
    expect(rows.map((el) => el.getAttribute('data-testid'))).toEqual([
      'request-card-sla-timer-t-new',
      'request-card-sla-timer-t-old',
    ]);
    expect(within(panel).getByTestId('request-card-sla-timer-t-new-value')).toHaveTextContent('осталось');
    expect(within(panel).getByTestId('request-card-sla-timer-t-old-value')).toHaveTextContent('завершён');
  });

  it('клиенту блок не показывается, и запрос SLA не отправляется', async () => {
    const fetchMock = open('CLIENT_OWNER', userWith({ companyRoles: ['CLIENT_OWNER'] }), () => jsonResponse(200, sla));

    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('request-card-sla')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/sla'))).toBe(false);
  });

  it('если таймеров нет, блока нет', async () => {
    const fetchMock = open('COORDINATOR', userWith({ operatorRoles: ['COORDINATOR'] }), () =>
      jsonResponse(200, { requestId: 'r-1', timers: [], pauses: [] }),
    );

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/sla'))).toBe(true));
    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('request-card-sla')).not.toBeInTheDocument();
  });

  it('при сбое показывает понятный текст и кнопку «Обновить», карточка остаётся', async () => {
    const user = userEvent.setup();
    let fail = true;
    open('COORDINATOR', userWith({ operatorRoles: ['COORDINATOR'] }), () =>
      fail ? jsonResponse(404, { code: 'NOT_FOUND', retryable: false }) : jsonResponse(200, sla),
    );

    const notice = await screen.findByTestId('request-card-sla-error');
    expect(notice).toHaveTextContent(t.loadError);
    expect(screen.getByTestId('request-card-title')).toBeInTheDocument();

    fail = false;
    await user.click(within(notice).getByRole('button', { name: texts.common.refresh }));
    expect(await screen.findByTestId('request-card-sla')).toBeInTheDocument();
  });
});

describe('Подписи завершённых и просроченных сроков', () => {
  it('завершённый срок — без остатка, с отметкой о просрочке, если она была', () => {
    expect(slaSnapshot(timer({ status: 'STOPPED' }))).toMatchObject({ state: 'stopped', valueLabel: 'завершён' });
    expect(slaSnapshot(timer({ status: 'STOPPED', breachedAt: '2026-11-14T11:00:00Z' }))).toMatchObject({
      state: 'breach',
      valueLabel: 'завершён с просрочкой',
    });
  });

  it('просрочка от breachedAt считается в настоящем времени, а не в рабочих днях', () => {
    const breachedAt = new Date(Date.now() - 24 * 3_600_000).toISOString();
    const snapshot = slaSnapshot(timer({ remainingSlaSeconds: 0, overdue: true, breachedAt }));
    expect(snapshot.valueLabel).toBe('просрочено на 1 д.');
  });
});
