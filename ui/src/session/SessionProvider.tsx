import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureApi } from '../api/client';
import { authApi, queryKeys } from '../api/endpoints';
import type { CompanyBrief, LoginRequest, Role, UserView } from '../api/types';
import { availableRoles, resolveActiveRole } from './roles';
import { sessionStore } from './storage';

export type SessionStatus = 'anonymous' | 'loading' | 'error' | 'authenticated';

export interface Session {
  status: SessionStatus;
  user?: UserView;
  /** Компания пользователя: в MVP одна (roles.md §10). */
  company?: CompanyBrief;
  roles: Role[];
  /** null — гость: нет компании и ролей оператора. */
  activeRole: Role | null;
  /** Ошибка загрузки профиля (сбой связи) — экран показывает «Обновить». */
  profileError: unknown;
  login: (credentials: LoginRequest) => Promise<UserView>;
  logout: (reason?: 'expired') => void;
  setActiveRole: (role: Role) => void;
  reloadProfile: () => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(() => sessionStore.getToken());
  const [storedRole, setStoredRole] = useState<Role | null>(() => sessionStore.getActiveRole());

  const meQuery = useQuery({
    queryKey: queryKeys.me,
    queryFn: authApi.me,
    enabled: token !== null,
    staleTime: 5 * 60_000,
  });

  const user = token ? meQuery.data : undefined;
  const activeRole = resolveActiveRole(user, storedRole);

  // Роль из хранилища больше не принадлежит пользователю — запоминаем вычисленную
  useEffect(() => {
    if (user && activeRole !== storedRole) {
      sessionStore.setActiveRole(activeRole);
      setStoredRole(activeRole);
    }
  }, [user, activeRole, storedRole]);

  const logout = useCallback(
    (reason?: 'expired') => {
      sessionStore.clear();
      if (reason === 'expired') sessionStore.markExpired();
      queryClient.clear();
      setToken(null);
      setStoredRole(null);
    },
    [queryClient],
  );

  // Слой запросов читает токен и роль напрямую из хранилища, а о 401 и отозванной роли сообщает сюда
  useEffect(() => {
    configureApi({
      getToken: () => sessionStore.getToken(),
      getActiveRole: () => sessionStore.getActiveRole(),
      onUnauthorized: () => logout('expired'),
      onAccessDenied: () => void queryClient.invalidateQueries({ queryKey: queryKeys.me }),
    });
  }, [logout, queryClient]);

  const login = useCallback(
    async (credentials: LoginRequest) => {
      const response = await authApi.login(credentials);
      sessionStore.setToken(response.accessToken, response.expiresIn);
      const role = resolveActiveRole(response.user, sessionStore.getActiveRole());
      sessionStore.setActiveRole(role);
      queryClient.setQueryData(queryKeys.me, response.user);
      setStoredRole(role);
      setToken(response.accessToken);
      return response.user;
    },
    [queryClient],
  );

  const setActiveRole = useCallback(
    (role: Role) => {
      sessionStore.setActiveRole(role);
      setStoredRole(role);
      // Данные другой роли не должны остаться на экране: сбрасываем всё, кроме профиля
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== queryKeys.me[0] });
    },
    [queryClient],
  );

  const reloadProfile = useCallback(() => void meQuery.refetch(), [meQuery]);

  const status: SessionStatus = !token ? 'anonymous' : user ? 'authenticated' : meQuery.isError ? 'error' : 'loading';

  const value = useMemo<Session>(
    () => ({
      status,
      user,
      company: user?.companies?.[0],
      roles: availableRoles(user),
      activeRole: user ? activeRole : null,
      profileError: meQuery.error,
      login,
      logout,
      setActiveRole,
      reloadProfile,
    }),
    [status, user, activeRole, meQuery.error, login, logout, setActiveRole, reloadProfile],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession вне SessionProvider');
  return session;
}
