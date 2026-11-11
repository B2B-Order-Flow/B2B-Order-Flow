import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../components/layout/AppLayout';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { routeAccess } from '../session/roles';
import { texts } from '../texts/ru';
import { HomeRedirect, RequireAuth, RoleRoute } from './guards';

const s = texts.screens;

// Адреса экранов — ui-requirements.md §3.4. Заглушки заменяются экранами по мере реализации.
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <PlaceholderPage id="UI-02" title={s.register} /> },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomeRedirect /> },
      {
        path: 'company/new',
        element: (
          <RoleRoute access={routeAccess.companyNew}>
            <PlaceholderPage id="UI-03" title={s.companyNew} />
          </RoleRoute>
        ),
      },
      {
        path: 'company',
        element: (
          <RoleRoute access={routeAccess.company}>
            <PlaceholderPage id="UI-04" title={s.company} />
          </RoleRoute>
        ),
      },
      {
        path: 'admin/companies',
        element: (
          <RoleRoute access={routeAccess.adminCompanies}>
            <PlaceholderPage id="UI-06" title={s.adminCompanies} />
          </RoleRoute>
        ),
      },
      {
        path: 'requests',
        element: (
          <RoleRoute access={routeAccess.requests}>
            <PlaceholderPage id="UI-05" title={s.requests} />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/new',
        element: (
          <RoleRoute access={routeAccess.requestForm}>
            <PlaceholderPage id="UI-07" title={s.requestNew} />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/:requestId',
        element: (
          <RoleRoute access={routeAccess.requests}>
            <PlaceholderPage id="UI-08" title={s.requestCard} />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/:requestId/edit',
        element: (
          <RoleRoute access={routeAccess.requestForm}>
            <PlaceholderPage id="UI-07" title={s.requestEdit} />
          </RoleRoute>
        ),
      },
      {
        path: 'manager-queue',
        element: (
          <RoleRoute access={routeAccess.managerQueue}>
            <PlaceholderPage id="UI-09" title={s.managerQueue} />
          </RoleRoute>
        ),
      },
      {
        path: 'queue',
        element: (
          <RoleRoute access={routeAccess.queue}>
            <PlaceholderPage id="UI-11" title={s.queue} />
          </RoleRoute>
        ),
      },
      {
        path: 'my-tasks',
        element: (
          <RoleRoute access={routeAccess.myTasks}>
            <PlaceholderPage id="UI-12" title={s.myTasks} />
          </RoleRoute>
        ),
      },
      {
        path: 'notifications',
        element: (
          <RoleRoute access={routeAccess.notifications}>
            <PlaceholderPage id="UI-14" title={s.notifications} />
          </RoleRoute>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
