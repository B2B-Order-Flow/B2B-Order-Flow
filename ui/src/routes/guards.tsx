import Box from '@mui/material/Box';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ErrorState, LoadingState } from '../components/states';
import { canAccess, homePath, type RouteAccess } from '../session/roles';
import { useSession } from '../session/SessionProvider';

/** Защищённая часть: без сессии — на вход с возвратом; пока грузится профиль — загрузка. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, profileError, reloadProfile } = useSession();
  const location = useLocation();

  if (status === 'anonymous') {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?from=${encodeURIComponent(from)}`} replace />;
  }
  if (status === 'error') {
    return (
      <Box sx={{ p: 4 }}>
        <ErrorState error={profileError} onRetry={reloadProfile} />
      </Box>
    );
  }
  if (status === 'loading') return <LoadingState />;
  return <>{children}</>;
}

/** Экран не для активной роли — на главный экран роли (ui-requirements.md §3.2). */
export function RoleRoute({ access, children }: { access: RouteAccess; children: ReactNode }) {
  const { activeRole } = useSession();
  if (!canAccess(access, activeRole)) return <Navigate to={homePath(activeRole)} replace />;
  return <>{children}</>;
}

/** Корень приложения: главный экран активной роли. */
export function HomeRedirect() {
  const { activeRole } = useSession();
  return <Navigate to={homePath(activeRole)} replace />;
}
