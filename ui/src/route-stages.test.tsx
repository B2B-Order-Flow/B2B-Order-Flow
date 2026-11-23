import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { RequestView, Role, StageView, UserView } from './api/types';
import { sessionStore } from './session/storage';
import { texts } from './texts/ru';
import { jsonResponse, mockApi, renderApp } from './test/utils';

// Фактические этапы маршрута в карточке: RequestView.stages (план недели 2, раздел 4, п. 2).

const t = texts.requestCard;

const stage = (patch: Partial<StageView>): StageView => ({
  id: 's-1',
  requestId: 'r-1',
  type: 'TECH_CHECK',
  status: 'DONE',
  queueRole: 'TECH_SPECIALIST',
  assigneeId: 'u-orlov',
  requiredByRules: [],
  routeVersion: 2,
  version: 2,
  createdAt: '2026-11-14T10:00:00Z',
  ...patch,
});

const stages: StageView[] = [
  stage({ id: 's-1' }),
  stage({
    id: 's-2',
    type: 'LEGAL_APPROVAL',
    status: 'IN_PROGRESS',
    queueRole: 'LAWYER',
    assigneeId: 'u-1',
    assigneeName: 'Анна Смирнова',
    requiredByRules: ['R-01', 'R-02'],
  }),
  stage({
    id: 's-3',
    type: 'FINANCE_APPROVAL',
    status: 'QUEUED',
    queueRole: 'FINANCE_CONTROLLER',
    assigneeId: undefined,
  }),
];

const request = (patch: Partial<RequestView> = {}): RequestView => ({
  id: 'r-1',
  number: 'REQ-2026-000300',
  companyId: 'c-1',
  status: 'IN_APPROVAL',
  version: 3,
  managerId: 'u-manager',
  routeVersion: 2,
  createdAt: '2026-11-13T11:00:00Z',
  address: 'г. Москва, ул. Лесная, д. 3',
  stages,
  ...patch,
});

const empty = { content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } };
const person = (patch: Partial<UserView>): UserView => ({
  id: 'u-1',
  login: 'user',
  fullName: 'Анна Смирнова',
  email: 'u@example.test',
  ...patch,
});

function open(role: Role, user: UserView, view: RequestView, extra: Parameters<typeof mockApi>[0] = {}) {
  sessionStore.setToken('jwt', 28800);
  sessionStore.setActiveRole(role);
  const fetchMock = mockApi({
    'GET /me': () => jsonResponse(200, user),
    'GET /requests/r-1': () => jsonResponse(200, view),
    'GET /requests/r-1/history': () => jsonResponse(200, empty),
    'GET /requests/r-1/sla': () => jsonResponse(200, { requestId: 'r-1', timers: [], pauses: [] }),
    ...extra,
  });
  renderApp('/requests/r-1');
  return fetchMock;
}

const row = async (type: string) => {
  const route = await screen.findByTestId('request-card-route');
  const found = within(route)
    .getAllByTestId('route-phase')
    .find((el) => el.getAttribute('data-stage') === type);
  if (!found) throw new Error(`Нет этапа ${type}`);
  return found;
};

describe('Этапы маршрута в карточке', () => {
  it('показывает созданные этапы в порядке ядра и их статусы вместо общих фаз', async () => {
    open('B2B_HEAD', person({ operatorRoles: ['B2B_HEAD'] }), request());

    const route = await screen.findByTestId('request-card-route');
    const rows = within(route).getAllByTestId('route-phase');
    expect(rows.map((el) => el.getAttribute('data-stage'))).toEqual([
      'TECH_CHECK',
      'LEGAL_APPROVAL',
      'FINANCE_APPROVAL',
    ]);
    expect(rows.map((el) => el.getAttribute('data-state'))).toEqual(['DONE', 'IN_PROGRESS', 'QUEUED']);
    expect(rows[1]).toHaveTextContent(texts.stageType.LEGAL_APPROVAL);
    expect(rows[1]).toHaveTextContent(texts.stageStatus.IN_PROGRESS);
    expect(within(route).queryByText('Коммерческие условия')).not.toBeInTheDocument();
    expect(within(route).getByTestId('route-more')).toHaveTextContent(t.routeMore);
  });

  it('клиенту — только названия и статусы, даже если ядро вернуло правила', async () => {
    open('CLIENT_OWNER', person({ companyRoles: ['CLIENT_OWNER'] }), request());

    const legal = await row('LEGAL_APPROVAL');
    expect(legal).toHaveTextContent(texts.stageStatus.IN_PROGRESS);
    expect(screen.queryByTestId('route-stage-assignee')).not.toBeInTheDocument();
    expect(screen.queryByTestId('route-stage-rules')).not.toBeInTheDocument();
    expect(legal).not.toHaveTextContent('R-01');
    expect(legal).not.toHaveTextContent('Анна Смирнова');
  });

  it('оператору — исполнитель и правила маршрутизации', async () => {
    open('LAWYER', person({ id: 'u-1', operatorRoles: ['LAWYER'] }), request());

    const legal = await row('LEGAL_APPROVAL');
    expect(within(legal).getByTestId('route-stage-assignee')).toHaveTextContent(t.stageAssignee(t.stageYou));
    expect(within(legal).getByTestId('route-stage-rules')).toHaveTextContent('R-01, R-02');
    const finance = await row('FINANCE_APPROVAL');
    expect(within(finance).getByTestId('route-stage-assignee')).toHaveTextContent(t.stageUnassigned);
    const tech = await row('TECH_CHECK');
    expect(within(tech).getByTestId('route-stage-assignee')).toHaveTextContent(t.stageAssigned);
    expect(within(tech).queryByTestId('route-stage-rules')).not.toBeInTheDocument();
  });

  it.each<[Role, UserView]>([
    ['COORDINATOR', person({ operatorRoles: ['COORDINATOR'] })],
    ['ENGINEER', person({ operatorRoles: ['ENGINEER'] })],
  ])('%s видит имя исполнителя из ответа ядра, список сотрудников не запрашивается', async (role, user) => {
    const view = request({ stages: [stage({ assigneeName: 'Павел Орлов' })] });
    const fetchMock = open(role, user, view);

    expect(await screen.findByTestId('route-stage-assignee')).toHaveTextContent(t.stageAssignee('Павел Орлов'));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/users'))).toBe(false);
  });

  it('свой этап подписан «Вы», даже если ядро вернуло имя', async () => {
    const view = request({
      stages: [stage({ assigneeId: 'u-1', assigneeName: 'Анна Смирнова', status: 'IN_PROGRESS' })],
    });
    open('B2B_HEAD', person({ id: 'u-1', operatorRoles: ['B2B_HEAD'] }), view);

    expect(await screen.findByTestId('route-stage-assignee')).toHaveTextContent(t.stageAssignee(t.stageYou));
  });

  it('если имени нет, показывает «Исполнитель назначен»', async () => {
    const fetchMock = open('COORDINATOR', person({ operatorRoles: ['COORDINATOR'] }), request({ stages: [stage({})] }));

    expect(await screen.findByTestId('route-stage-assignee')).toHaveTextContent(t.stageAssigned);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/users'))).toBe(false);
  });

  it('показывает, что этап нужно переназначить', async () => {
    const view = request({ stages: [stage({ status: 'IN_PROGRESS', needsReassignment: true })] });
    open('B2B_HEAD', person({ operatorRoles: ['B2B_HEAD'] }), view);

    expect(await screen.findByTestId('route-stage-assignee')).toHaveTextContent(t.stageReassign);
  });

  it('до отправки в работу этапов нет, показывается подсказка', async () => {
    open('CLIENT_OWNER', person({ companyRoles: ['CLIENT_OWNER'] }), request({ status: 'DATA_CHECK', stages: [] }));

    expect(await screen.findByTestId('route-empty')).toHaveTextContent(t.routeEmpty);
    expect(screen.queryByTestId('route-phase')).not.toBeInTheDocument();
  });

  it('у завершённой заявки нет подсказки о следующих этапах', async () => {
    const view = request({ status: 'REJECTED', stages: [stage({ status: 'REJECTED', type: 'LEGAL_APPROVAL' })] });
    open('B2B_HEAD', person({ operatorRoles: ['B2B_HEAD'] }), view);

    expect(await row('LEGAL_APPROVAL')).toHaveAttribute('data-state', 'REJECTED');
    expect(screen.queryByTestId('route-more')).not.toBeInTheDocument();
  });
});
