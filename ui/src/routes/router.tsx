import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../components/layout/AppLayout';
import { AdminCompaniesPage } from '../pages/AdminCompaniesPage';
import { CompanyNewPage } from '../pages/CompanyNewPage';
import { CompanyPage } from '../pages/CompanyPage';
import { LoginPage } from '../pages/LoginPage';
import { ManagerQueuePage } from '../pages/ManagerQueuePage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { RegisterPage } from '../pages/RegisterPage';
import { RequestCardPage } from '../pages/request/RequestCardPage';
import { RequestFormPage } from '../pages/RequestFormPage';
import { RequestsPage } from '../pages/RequestsPage';
import { MyTasksPage } from '../pages/stages/MyTasksPage';
import { QueuePage } from '../pages/stages/QueuePage';
import { routeAccess } from '../session/roles';
import { HomeRedirect, RequireAuth, RoleRoute } from './guards';

// Адреса экранов — ui-requirements.md §3.4.
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
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
            <CompanyNewPage />
          </RoleRoute>
        ),
      },
      {
        path: 'company',
        element: (
          <RoleRoute access={routeAccess.company}>
            <CompanyPage />
          </RoleRoute>
        ),
      },
      {
        path: 'admin/companies',
        element: (
          <RoleRoute access={routeAccess.adminCompanies}>
            <AdminCompaniesPage />
          </RoleRoute>
        ),
      },
      {
        path: 'requests',
        element: (
          <RoleRoute access={routeAccess.requests}>
            <RequestsPage />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/new',
        element: (
          <RoleRoute access={routeAccess.requestForm}>
            <RequestFormPage />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/:requestId',
        element: (
          <RoleRoute access={routeAccess.requests}>
            <RequestCardPage />
          </RoleRoute>
        ),
      },
      {
        path: 'requests/:requestId/edit',
        element: (
          <RoleRoute access={routeAccess.requestForm}>
            <RequestFormPage />
          </RoleRoute>
        ),
      },
      {
        path: 'manager-queue',
        element: (
          <RoleRoute access={routeAccess.managerQueue}>
            <ManagerQueuePage />
          </RoleRoute>
        ),
      },
      {
        path: 'queue',
        element: (
          <RoleRoute access={routeAccess.queue}>
            <QueuePage />
          </RoleRoute>
        ),
      },
      {
        path: 'my-tasks',
        element: (
          <RoleRoute access={routeAccess.myTasks}>
            <MyTasksPage />
          </RoleRoute>
        ),
      },
      {
        path: 'notifications',
        element: (
          <RoleRoute access={routeAccess.notifications}>
            <NotificationsPage />
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
