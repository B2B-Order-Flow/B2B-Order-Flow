import { describe, expect, it } from 'vitest';
import type { UserView } from '../api/types';
import { availableRoles, canAccess, homePath, menuFor, resolveActiveRole, routeAccess } from './roles';

const user = (companyRoles: UserView['companyRoles'], operatorRoles: UserView['operatorRoles']): UserView => ({
  id: 'u1',
  login: 'user',
  fullName: 'Пользователь',
  email: 'user@example.test',
  companyRoles,
  operatorRoles,
});

describe('роли пользователя', () => {
  it('упорядочивает роли как в переключателе', () => {
    expect(availableRoles(user([], ['ENGINEER', 'TECH_SPECIALIST']))).toEqual(['TECH_SPECIALIST', 'ENGINEER']);
  });

  it('берёт сохранённую роль, если она ещё есть у пользователя, иначе первую', () => {
    const kim = user([], ['TECH_SPECIALIST', 'ENGINEER']);
    expect(resolveActiveRole(kim, 'ENGINEER')).toBe('ENGINEER');
    expect(resolveActiveRole(kim, 'LAWYER')).toBe('TECH_SPECIALIST');
    expect(resolveActiveRole(kim, null)).toBe('TECH_SPECIALIST');
  });

  it('пользователь без компании и ролей оператора — гость', () => {
    expect(resolveActiveRole(user([], []), null)).toBeNull();
    expect(homePath(null)).toBe('/company/new');
  });
});

describe('главный экран и меню', () => {
  it.each([
    ['CLIENT_OWNER', '/requests'],
    ['CLIENT_ADMIN', '/company'],
    ['SALES_MANAGER', '/requests'],
    ['TECH_SPECIALIST', '/my-tasks'],
    ['ENGINEER', '/my-tasks'],
    ['COORDINATOR', '/requests'],
    ['ADMIN', '/admin/companies'],
  ] as const)('%s → %s', (role, path) => {
    expect(homePath(role)).toBe(path);
  });

  it('у каждого пункта меню роли есть доступ к экрану', () => {
    const accessByPath: Record<string, (typeof routeAccess)[keyof typeof routeAccess]> = {
      '/company/new': routeAccess.companyNew,
      '/company': routeAccess.company,
      '/admin/companies': routeAccess.adminCompanies,
      '/requests': routeAccess.requests,
      '/requests/new': routeAccess.requestForm,
      '/manager-queue': routeAccess.managerQueue,
      '/queue': routeAccess.queue,
      '/my-tasks': routeAccess.myTasks,
    };
    const roles = [
      null,
      ...availableRoles(
        user(
          ['CLIENT_OWNER', 'CLIENT_ADMIN', 'CLIENT_REQUESTER'],
          [
            'SALES_MANAGER',
            'TECH_SPECIALIST',
            'LAWYER',
            'FINANCE_CONTROLLER',
            'PROCUREMENT_SPECIALIST',
            'B2B_HEAD',
            'ENGINEER',
            'COORDINATOR',
            'ADMIN',
          ],
        ),
      ),
    ];
    for (const role of roles) {
      for (const item of menuFor(role)) {
        expect(canAccess(accessByPath[item.path]!, role), `${role} → ${item.path}`).toBe(true);
      }
      expect(canAccess(accessByPath[homePath(role)]!, role), `${role} → главный экран`).toBe(true);
    }
  });

  it('администратор не видит заявки', () => {
    expect(canAccess(routeAccess.requests, 'ADMIN')).toBe(false);
    expect(menuFor('ADMIN').map((item) => item.path)).toEqual(['/admin/companies']);
  });
});
