import { COMPANY_ROLES, OPERATOR_ROLES, type CompanyRole, type Role, type UserView } from '../api/types';
import { texts } from '../texts/ru';

// Роли, меню и доступ к экранам по ui-requirements.md §3.2–3.4.
// Это только то, что показывать: права проверяет ядро.

/** Порядок ролей в переключателе и для выбора роли по умолчанию (§3.3). */
export const ROLE_ORDER: readonly Role[] = [...COMPANY_ROLES, ...OPERATOR_ROLES];

const CLIENT_CREATORS: readonly Role[] = ['CLIENT_OWNER', 'CLIENT_REQUESTER'];
const STAGE_EXECUTORS: readonly Role[] = [
  'TECH_SPECIALIST',
  'ENGINEER',
  'LAWYER',
  'FINANCE_CONTROLLER',
  'PROCUREMENT_SPECIALIST',
  'B2B_HEAD',
];

export function isCompanyRole(role: Role | null): role is CompanyRole {
  return role !== null && (COMPANY_ROLES as readonly string[]).includes(role);
}

/** Все роли пользователя в порядке ROLE_ORDER. */
export function availableRoles(user: UserView | undefined): Role[] {
  if (!user) return [];
  const own = new Set<Role>([...(user.companyRoles ?? []), ...(user.operatorRoles ?? [])]);
  return ROLE_ORDER.filter((role) => own.has(role));
}

/** Роль по умолчанию: сохранённая, если она ещё есть у пользователя, иначе первая по порядку; null — гость. */
export function resolveActiveRole(user: UserView | undefined, stored: Role | null): Role | null {
  const roles = availableRoles(user);
  if (stored && roles.includes(stored)) return stored;
  return roles[0] ?? null;
}

/** Главный экран роли (§3.2). null — гость без компании и ролей оператора. */
export function homePath(role: Role | null): string {
  switch (role) {
    case null:
      return '/company/new';
    case 'CLIENT_ADMIN':
      return '/company';
    case 'ADMIN':
      return '/admin/companies';
    case 'TECH_SPECIALIST':
    case 'ENGINEER':
    case 'LAWYER':
    case 'FINANCE_CONTROLLER':
    case 'PROCUREMENT_SPECIALIST':
      return '/my-tasks';
    default:
      return '/requests';
  }
}

export interface MenuItem {
  path: string;
  label: string;
}

/** Пункты меню активной роли (§3.2). */
export function menuFor(role: Role | null): MenuItem[] {
  const m = texts.menu;
  switch (role) {
    case null:
      return [{ path: '/company/new', label: m.companyNew }];
    case 'CLIENT_OWNER':
    case 'CLIENT_REQUESTER':
      return [
        { path: '/requests', label: m.requests },
        { path: '/requests/new', label: m.requestNew },
        { path: '/company', label: m.company },
      ];
    case 'CLIENT_ADMIN':
      return [{ path: '/company', label: m.company }];
    case 'SALES_MANAGER':
      return [
        { path: '/requests', label: m.myRequests },
        { path: '/manager-queue', label: m.managerQueue },
      ];
    case 'TECH_SPECIALIST':
    case 'ENGINEER':
    case 'LAWYER':
    case 'FINANCE_CONTROLLER':
    case 'PROCUREMENT_SPECIALIST':
      return [
        { path: '/my-tasks', label: m.myTasks },
        { path: '/queue', label: m.queue },
        { path: '/requests', label: m.requests },
      ];
    case 'B2B_HEAD':
      return [
        { path: '/requests', label: m.requests },
        { path: '/my-tasks', label: m.myTasks },
      ];
    case 'COORDINATOR':
      return [
        { path: '/requests', label: m.requests },
        { path: '/manager-queue', label: m.managerQueue },
        { path: '/queue', label: m.stageQueues },
      ];
    case 'ADMIN':
      return [{ path: '/admin/companies', label: m.adminCompanies }];
  }
}

/** Кому открыт экран (§3.4). null в списке — гость. */
export type RouteAccess = 'any' | readonly (Role | null)[];

const NOT_ADMIN_OR_GUEST: readonly Role[] = ROLE_ORDER.filter((r) => r !== 'ADMIN');

export const routeAccess = {
  companyNew: [null],
  company: COMPANY_ROLES,
  adminCompanies: ['ADMIN'],
  requests: NOT_ADMIN_OR_GUEST.filter((r) => r !== 'CLIENT_ADMIN'),
  requestForm: CLIENT_CREATORS,
  managerQueue: ['SALES_MANAGER', 'COORDINATOR'],
  queue: [...STAGE_EXECUTORS.filter((r) => r !== 'B2B_HEAD'), 'COORDINATOR'],
  myTasks: STAGE_EXECUTORS,
  notifications: NOT_ADMIN_OR_GUEST,
} satisfies Record<string, RouteAccess>;

export function canAccess(access: RouteAccess, role: Role | null): boolean {
  return access === 'any' || access.includes(role);
}

export function canCreateRequest(role: Role | null): boolean {
  return role !== null && CLIENT_CREATORS.includes(role);
}
