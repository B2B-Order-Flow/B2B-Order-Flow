import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { RequestView, Role, RouteView, StageView, UserView } from './api/types';
import { menuFor } from './session/roles';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Согласования недели 3: решение в карточке, волны маршрута, очереди со сводкой срока (plan-week3.md §4, п. 1).

const t = texts.approval;

const person = (id: string, roles: Role[]): UserView => ({
  id,
  login: id,
  fullName: 'Елена Волкова',
  email: `${id}@example.test`,
  operatorRoles: roles as UserView['operatorRoles'],
});
const lawyer = person('u-volkova', ['LAWYER']);
const finance = person('u-belov', ['FINANCE_CONTROLLER']);

const stage = (patch: Partial<StageView>): StageView => ({
  id: 's-legal',
  requestId: 'r-1',
  requestNumber: 'REQ-2026-000140',
  type: 'LEGAL_APPROVAL',
  status: 'IN_PROGRESS',
  queueRole: 'LAWYER',
  assigneeId: 'u-volkova',
  requiredByRules: ['R-01'],
  routeVersion: 2,
  version: 3,
  createdAt: '2026-11-25T09:00:00Z',
  ...patch,
});

const request = (stages: StageView[], patch: Partial<RequestView> = {}): RequestView => ({
  id: 'r-1',
  number: 'REQ-2026-000140',
  companyId: 'c-1',
  companyName: 'ООО «Логистик Плюс»',
  status: 'IN_APPROVAL',
  version: 7,
  managerId: 'u-smirnova',
  routeVersion: 2,
  createdAt: '2026-11-20T09:00:00Z',
  address: 'склад, ул. Складская, д. 5',
  requestedSpeedMbps: 500,
  selectedOfferId: 'o-1',
  offers: [
    { id: 'o-1', status: 'SELECTED', tariffType: 'INDIVIDUAL', discountPercent: '15.00', contractType: 'CLIENT_FORM' },
  ],
  segment: 'LARGE',
  debtAmount: '0.00',
  stages,
  ...patch,
});

const empty = { content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } };
const reasons = {
  name: 'rejection-reasons',
  items: [
    {
      code: 'LEGAL_TERMS_UNACCEPTABLE',
      title: 'Условия договора неприемлемы для оператора',
      stageType: 'LEGAL_APPROVAL',
    },
  ],
};
const bodyOf = (init: RequestInit) => JSON.parse(String(init.body));

function open(role: Role, user: UserView, view: RequestView, extra: Parameters<typeof mockApi>[0] = {}) {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
  const fetchMock = mockApi({
    'GET /me': () => jsonResponse(200, user),
    'GET /requests/r-1': () => jsonResponse(200, view),
    'GET /requests/r-1/history': () => jsonResponse(200, empty),
    'GET /requests/r-1/sla': () => jsonResponse(200, { requestId: 'r-1', timers: [], pauses: [] }),
    'GET /dictionaries/rejection-reasons': () => jsonResponse(200, reasons),
    'POST /stages/s-legal/decision': (_, init) =>
      jsonResponse(200, stage({ status: 'DONE', version: bodyOf(init).version + 1 })),
    'POST /stages/s-legal/claim': () => jsonResponse(200, stage({ version: 4 })),
    ...extra,
  });
  renderApp('/requests/r-1');
  return fetchMock;
}

const decisionCall = (fetchMock: ReturnType<typeof mockApi>) =>
  fetchMock.mock.calls.find(
    ([url, init]) => String(url).endsWith('/stages/s-legal/decision') && init?.method === 'POST',
  );

describe('Решение согласующего в карточке', () => {
  it('«Согласовать» отправляет решение с версией этапа', async () => {
    const fetchMock = open('LAWYER', lawyer, request([stage({})]));
    const user = userEvent.setup();

    const form = await screen.findByTestId('approval-decision');
    expect(screen.getByTestId('approval-terms')).toHaveTextContent(texts.contractType.CLIENT_FORM);
    await user.click(within(form).getByTestId('decision-option-APPROVE'));
    await user.click(within(form).getByTestId('action-decision'));

    await waitFor(() => expect(decisionCall(fetchMock)).toBeTruthy());
    expect(bodyOf(decisionCall(fetchMock)![1]!)).toEqual({ decision: 'APPROVE', version: 3 });
  });

  it('возврат требует комментарий: что исправить', async () => {
    const fetchMock = open('LAWYER', lawyer, request([stage({})]));
    const user = userEvent.setup();

    const form = await screen.findByTestId('approval-decision');
    await user.click(within(form).getByTestId('decision-option-RETURN'));
    await user.click(within(form).getByTestId('action-decision'));
    expect(await within(form).findByText(texts.common.required)).toBeInTheDocument();
    expect(decisionCall(fetchMock)).toBeUndefined();

    await user.type(within(form).getByTestId('decision-comment'), 'Нужен протокол разногласий по п. 5.2');
    await user.click(within(form).getByTestId('action-decision'));
    await waitFor(() => expect(decisionCall(fetchMock)).toBeTruthy());
    expect(bodyOf(decisionCall(fetchMock)![1]!)).toEqual({
      decision: 'RETURN',
      comment: 'Нужен протокол разногласий по п. 5.2',
      version: 3,
    });
  });

  it('отклонение: причина своего шага и OTHER, комментарий и подтверждение', async () => {
    const fetchMock = open('LAWYER', lawyer, request([stage({})]));
    const user = userEvent.setup();

    const form = await screen.findByTestId('approval-decision');
    await user.click(within(form).getByTestId('decision-option-REJECT'));
    const select = within(form).getByTestId('decision-reason');
    await waitFor(() => expect(within(select).getAllByRole('option').length).toBe(3));
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.getAttribute('value')),
    ).toEqual(['', 'LEGAL_TERMS_UNACCEPTABLE', 'OTHER']);

    await user.click(within(form).getByTestId('action-decision'));
    expect(await within(form).findAllByText(texts.common.required)).toHaveLength(2);

    await user.selectOptions(select, 'LEGAL_TERMS_UNACCEPTABLE');
    await user.type(within(form).getByTestId('decision-comment'), 'Неограниченная ответственность оператора');
    await user.click(within(form).getByTestId('action-decision'));
    expect(decisionCall(fetchMock)).toBeUndefined();
    await user.click(await screen.findByTestId('action-decision-confirm'));

    await waitFor(() => expect(decisionCall(fetchMock)).toBeTruthy());
    expect(bodyOf(decisionCall(fetchMock)![1]!)).toEqual({
      decision: 'REJECT',
      reasonCode: 'LEGAL_TERMS_UNACCEPTABLE',
      comment: 'Неограниченная ответственность оператора',
      version: 3,
    });
    const reasonsCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/dictionaries/rejection-reasons'));
    expect(String(reasonsCall![0])).toContain('stageType=LEGAL_APPROVAL');
  });

  it('если справочник недоступен, остаётся причина OTHER', async () => {
    open('LAWYER', lawyer, request([stage({})]), {
      'GET /dictionaries/rejection-reasons': () => jsonResponse(503, { code: 'SERVICE_UNAVAILABLE', retryable: true }),
    });
    const user = userEvent.setup();

    const form = await screen.findByTestId('approval-decision');
    await user.click(within(form).getByTestId('decision-option-REJECT'));
    const select = within(form).getByTestId('decision-reason');
    await waitFor(() =>
      expect(
        within(select)
          .getAllByRole('option')
          .map((o) => o.getAttribute('value')),
      ).toEqual(['', 'OTHER']),
    );
  });

  it('этап в очереди своей роли можно взять из карточки', async () => {
    const fetchMock = open('LAWYER', lawyer, request([stage({ status: 'QUEUED', assigneeId: undefined })]));
    const user = userEvent.setup();

    await user.click(await screen.findByTestId('action-claim'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/claim') && init?.method === 'POST'),
      ).toBe(true),
    );
    expect(screen.queryByTestId('approval-decision')).not.toBeInTheDocument();
  });

  it('другая роль согласующего формы чужого этапа не видит', async () => {
    open('FINANCE_CONTROLLER', finance, request([stage({})]));

    expect(await screen.findByTestId('request-card-title')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-decision')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-claim')).not.toBeInTheDocument();
  });

  it('менеджер заявки с ролью юриста видит пояснение, а не форму и не кнопку (EC-18)', async () => {
    const own = request([stage({ status: 'QUEUED', assigneeId: undefined })], { managerId: 'u-volkova' });
    open('LAWYER', lawyer, own);

    expect(await screen.findByTestId('approval-self')).toHaveTextContent(t.selfApproval);
    expect(screen.queryByTestId('approval-decision')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-claim')).not.toBeInTheDocument();
  });

  it('ответ ядра SELF_APPROVAL_FORBIDDEN показывается понятным текстом', async () => {
    open('LAWYER', lawyer, request([stage({})]), {
      'POST /stages/s-legal/decision': () => jsonResponse(403, { code: 'SELF_APPROVAL_FORBIDDEN', retryable: false }),
    });
    const user = userEvent.setup();

    const form = await screen.findByTestId('approval-decision');
    await user.click(within(form).getByTestId('decision-option-APPROVE'));
    await user.click(within(form).getByTestId('action-decision'));
    expect(await screen.findByTestId('toast')).toHaveTextContent('Нельзя согласовать заявку, в которой вы менеджер');
  });
});

describe('Волны согласований в маршруте', () => {
  const plan: RouteView = {
    requestId: 'r-1',
    routeVersion: 2,
    construction: true,
    stages: [
      { stageId: 's-tech', type: 'TECH_CHECK', status: 'DONE', wave: 0, requiredByRules: [] },
      { stageId: 's-legal', type: 'LEGAL_APPROVAL', wave: 1, requiredByRules: ['R-01'] },
      { stageId: 's-fin', type: 'FINANCE_APPROVAL', wave: 1, requiredByRules: ['R-03', 'R-05'] },
      { type: 'MGMT_APPROVAL', wave: 2, requiredByRules: ['R-07', 'R-08'] },
      { type: 'CONTRACT_SIGNING', wave: 0, requiredByRules: [] },
    ],
  };
  const created = [
    stage({
      id: 's-tech',
      type: 'TECH_CHECK',
      status: 'DONE',
      queueRole: 'TECH_SPECIALIST',
      assigneeName: 'Павел Орлов',
    }),
    stage({ id: 's-legal', status: 'DONE', assigneeName: 'Елена Волкова' }),
    stage({
      id: 's-fin',
      type: 'FINANCE_APPROVAL',
      status: 'RETURNED',
      queueRole: 'FINANCE_CONTROLLER',
      assigneeId: 'u-belov',
      assigneeName: 'Сергей Белов',
    }),
  ];

  it('показывает этапы по волнам, решения и ещё не созданное управленческое согласование', async () => {
    open('B2B_HEAD', person('u-gromov', ['B2B_HEAD']), request(created, { status: 'REWORK' }), {
      'GET /requests/r-1/route': () => jsonResponse(200, plan),
    });

    const route = await screen.findByTestId('request-card-route');
    await within(route).findByTestId('route-wave-2');
    expect(within(route).getByTestId('route-wave-1')).toHaveTextContent(texts.requestCard.routeWave(1));
    const rows = within(route).getAllByTestId('route-phase');
    expect(rows.map((el) => `${el.getAttribute('data-stage')}:${el.getAttribute('data-state')}`)).toEqual([
      'TECH_CHECK:DONE',
      'LEGAL_APPROVAL:DONE',
      'FINANCE_APPROVAL:RETURNED',
      'MGMT_APPROVAL:PLANNED',
      'CONTRACT_SIGNING:PLANNED',
    ]);
    expect(rows[1]).toHaveTextContent('Согласовано');
    expect(rows[1]).toHaveTextContent('Исполнитель: Елена Волкова');
    expect(rows[2]).toHaveTextContent('Возвращено на доработку');
    expect(rows[3]).toHaveTextContent(texts.requestCard.stagePlanned);
    expect(rows[3]).toHaveTextContent('R-07, R-08');
    expect(within(route).queryByTestId('route-more')).not.toBeInTheDocument();
  });

  it('клиенту план без исполнителей и правил', async () => {
    // Как в ответе ядра клиенту: служебных полей нет совсем, а не пустые значения
    const clientPlan: RouteView = {
      ...plan,
      stages: plan.stages.map(({ requiredByRules: _r, queueRole: _q, ...rest }) => rest),
    };
    open('CLIENT_OWNER', { ...person('u-fedorova', []), companyRoles: ['CLIENT_OWNER'] }, request(created), {
      'GET /requests/r-1/route': () => jsonResponse(200, clientPlan),
    });

    const route = await screen.findByTestId('request-card-route');
    await within(route).findByTestId('route-wave-1');
    expect(within(route).queryByTestId('route-stage-assignee')).not.toBeInTheDocument();
    expect(within(route).queryByTestId('route-stage-rules')).not.toBeInTheDocument();
  });
});

describe('Причина «нет ТВ» и комментарий', () => {
  it('комментарий обязателен только к OTHER, даже если справочник пометил другую причину', async () => {
    const orlov = person('u-orlov', ['TECH_SPECIALIST']);
    const tech = stage({
      id: 's-tech',
      type: 'TECH_CHECK',
      status: 'IN_PROGRESS',
      queueRole: 'TECH_SPECIALIST',
      assigneeId: 'u-orlov',
      version: 1,
    });
    const fetchMock = open('TECH_SPECIALIST', orlov, request([tech], { status: 'TECH_CHECK' }), {
      'GET /dictionaries/rejection-reasons': () =>
        jsonResponse(200, {
          name: 'rejection-reasons',
          items: [
            {
              code: 'TECH_NO_NETWORK',
              title: 'Нет сети оператора в районе подключения',
              stageType: 'TECH_CHECK',
              requiresComment: true,
            },
          ],
        }),
      'POST /stages/s-tech/tech-result': () => jsonResponse(200, { ...tech, status: 'DONE' }),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByTestId('tech-result-option-NONE'));
    const select = screen.getByTestId('tech-reason');
    await waitFor(() => expect(within(select).getAllByRole('option').length).toBe(3));
    await user.selectOptions(select, 'TECH_NO_NETWORK');
    await user.click(screen.getByTestId('action-tech-result'));
    await user.click(await screen.findByTestId('action-tech-result-confirm'));

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/stages/s-tech/tech-result'))).toBe(true),
    );
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/stages/s-tech/tech-result'));
    expect(bodyOf(call![1]!)).toEqual({ result: 'NONE', reasonCode: 'TECH_NO_NETWORK', version: 1 });
  });
});

describe('Маршрут завершившейся заявки', () => {
  it.each(['REJECTED', 'CANCELLED'] as const)(
    '%s: только фактические этапы и решения, без «Будет создан позже»',
    async (status) => {
      const plan: RouteView = {
        requestId: 'r-1',
        routeVersion: 2,
        stages: [
          { stageId: 's-tech', type: 'TECH_CHECK', status: 'DONE', wave: 0, requiredByRules: [] },
          { stageId: 's-legal', type: 'LEGAL_APPROVAL', status: 'REJECTED', wave: 1, requiredByRules: ['R-01'] },
          { stageId: 's-fin', type: 'FINANCE_APPROVAL', status: 'CANCELLED', wave: 1, requiredByRules: ['R-03'] },
          { type: 'MGMT_APPROVAL', wave: 2, requiredByRules: ['R-07'] },
          { type: 'CONTRACT_SIGNING', wave: 0, requiredByRules: [] },
        ],
      };
      const created = [
        stage({ id: 's-tech', type: 'TECH_CHECK', status: 'DONE', queueRole: 'TECH_SPECIALIST' }),
        stage({ id: 's-legal', status: 'REJECTED' }),
        stage({ id: 's-fin', type: 'FINANCE_APPROVAL', status: 'CANCELLED', queueRole: 'FINANCE_CONTROLLER' }),
      ];
      open('B2B_HEAD', person('u-gromov', ['B2B_HEAD']), request(created, { status }), {
        'GET /requests/r-1/route': () => jsonResponse(200, plan),
      });

      const route = await screen.findByTestId('request-card-route');
      await within(route).findByTestId('route-wave-1');
      const rows = within(route).getAllByTestId('route-phase');
      expect(rows.map((el) => `${el.getAttribute('data-stage')}:${el.getAttribute('data-state')}`)).toEqual([
        'TECH_CHECK:DONE',
        'LEGAL_APPROVAL:REJECTED',
        'FINANCE_APPROVAL:CANCELLED',
      ]);
      expect(rows[1]).toHaveTextContent('Отклонено');
      expect(within(route).queryByText(texts.requestCard.stagePlanned)).not.toBeInTheDocument();
      expect(within(route).queryByTestId('route-wave-2')).not.toBeInTheDocument();
    },
  );
});

describe('Очереди согласующих', () => {
  const queued = (id: string, number: string, sla: StageView['sla']) =>
    stage({ id, requestNumber: number, status: 'QUEUED', assigneeId: undefined, sla });

  it('срок этапа по сводке, сверху — просроченные и с меньшим остатком', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('LAWYER');
    mockApi({
      'GET /me': () => jsonResponse(200, lawyer),
      'GET /stages': () =>
        jsonResponse(200, {
          ...empty,
          content: [
            queued('s-a', 'REQ-A', { status: 'RUNNING', remainingMinutes: 600 }),
            queued('s-b', 'REQ-B', { status: 'BREACHED', remainingMinutes: -90, overdue: true }),
            queued('s-c', 'REQ-C', { status: 'RUNNING', remainingMinutes: 45 }),
            queued('s-d', 'REQ-D', { status: 'PAUSED', remainingMinutes: 10 }),
          ],
        }),
    });
    renderApp('/queue');

    await screen.findByTestId('queue-row-REQ-A');
    const order = screen.getAllByTestId(/^queue-row-/).map((el) => el.getAttribute('data-testid'));
    expect(order).toEqual(['queue-row-REQ-B', 'queue-row-REQ-C', 'queue-row-REQ-A', 'queue-row-REQ-D']);
    expect(within(screen.getByTestId('queue-row-REQ-B')).getByTestId('stage-sla-cell')).toHaveTextContent(
      'просрочено на 1 ч 30 мин',
    );
    expect(within(screen.getByTestId('queue-row-REQ-C')).getByTestId('stage-sla-cell')).toHaveTextContent(
      'осталось 45 мин',
    );
    expect(within(screen.getByTestId('queue-row-REQ-D')).getByTestId('stage-sla-cell')).toHaveTextContent('на паузе');
  });

  it('срок стройки — в календарных днях, согласований — в рабочем времени', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('COORDINATOR');
    mockApi({
      'GET /me': () => jsonResponse(200, person('u-kovaleva', ['COORDINATOR'])),
      'GET /stages': (url) =>
        jsonResponse(200, {
          ...empty,
          content:
            url.searchParams.get('queueRole') === 'ENGINEER'
              ? [
                  stage({
                    id: 's-build',
                    requestNumber: 'REQ-BUILD',
                    type: 'CONSTRUCTION',
                    status: 'QUEUED',
                    queueRole: 'ENGINEER',
                    assigneeId: undefined,
                    sla: { status: 'RUNNING', remainingMinutes: 1440 },
                  }),
                  stage({
                    id: 's-late',
                    requestNumber: 'REQ-LATE',
                    type: 'CONSTRUCTION',
                    status: 'QUEUED',
                    queueRole: 'ENGINEER',
                    assigneeId: undefined,
                    sla: { status: 'BREACHED', remainingMinutes: -2880, overdue: true },
                  }),
                ]
              : [queued('s-legal', 'REQ-LEGAL', { status: 'RUNNING', remainingMinutes: 1440 })],
        }),
    });
    const user = userEvent.setup();
    renderApp('/queue');

    expect(within(await screen.findByTestId('queue-row-REQ-LEGAL')).getByTestId('stage-sla-cell')).toHaveTextContent(
      'осталось 3 р.д.',
    );
    await user.selectOptions(screen.getByTestId('queue-role'), 'ENGINEER');
    expect(within(await screen.findByTestId('queue-row-REQ-BUILD')).getByTestId('stage-sla-cell')).toHaveTextContent(
      'осталось 1 д.',
    );
    expect(within(screen.getByTestId('queue-row-REQ-LATE')).getByTestId('stage-sla-cell')).toHaveTextContent(
      'просрочено на 2 д.',
    );
  });

  it('просроченный без остатка выше идущего с остатком в минуту', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('LAWYER');
    mockApi({
      'GET /me': () => jsonResponse(200, lawyer),
      'GET /stages': () =>
        jsonResponse(200, {
          ...empty,
          content: [
            queued('s-run', 'REQ-RUN', { status: 'RUNNING', remainingMinutes: 1 }),
            queued('s-late', 'REQ-LATE', { status: 'BREACHED' }),
          ],
        }),
    });
    renderApp('/queue');

    await screen.findByTestId('queue-row-REQ-LATE');
    const order = screen.getAllByTestId(/^queue-row-/).map((el) => el.getAttribute('data-testid'));
    expect(order).toEqual(['queue-row-REQ-LATE', 'queue-row-REQ-RUN']);
    const late = within(screen.getByTestId('queue-row-REQ-LATE')).getByTestId('stage-sla-cell');
    expect(late).toHaveTextContent('просрочено');
    expect(late).toHaveAttribute('data-state', 'BREACHED');
    expect(late).toHaveAttribute('data-overdue', 'true');
  });

  it('текст и data-state по одному правилу: пауза и завершение важнее просрочки', async () => {
    sessionStore.setToken('jwt', 28800);
    sessionStore.setActiveRole('LAWYER');
    mockApi({
      'GET /me': () => jsonResponse(200, lawyer),
      'GET /stages': () =>
        jsonResponse(200, {
          ...empty,
          content: [
            queued('s-p', 'REQ-PAUSED', { status: 'PAUSED', remainingMinutes: -30, overdue: true }),
            queued('s-s', 'REQ-STOPPED', { status: 'STOPPED', overdue: true }),
            queued('s-r', 'REQ-RUNNING', { status: 'RUNNING' }),
          ],
        }),
    });
    renderApp('/queue');

    const cell = async (number: string) =>
      within(await screen.findByTestId(`queue-row-${number}`)).getByTestId('stage-sla-cell');
    const paused = await cell('REQ-PAUSED');
    expect(paused).toHaveTextContent('на паузе, срок нарушен');
    expect(paused).toHaveAttribute('data-state', 'PAUSED');
    expect(paused).toHaveAttribute('data-overdue', 'true');
    const stopped = await cell('REQ-STOPPED');
    expect(stopped).toHaveTextContent('завершён с просрочкой');
    expect(stopped).toHaveAttribute('data-state', 'STOPPED');
    const running = await cell('REQ-RUNNING');
    expect(running).toHaveTextContent('идёт');
    expect(running).toHaveAttribute('data-state', 'RUNNING');
    expect(running).not.toHaveAttribute('data-overdue');
  });

  it('у руководителя B2B есть очередь управленческого согласования', () => {
    expect(menuFor('B2B_HEAD').map((item) => item.path)).toContain('/queue');
  });
});
