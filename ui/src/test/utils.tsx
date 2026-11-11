import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { createQueryClient } from '../api/queryClient';
import { AppProviders } from '../App';
import { routes } from '../routes/router';

/** Ответ fetch в формате API. */
export function jsonResponse(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  const type = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': type, ...headers },
  });
}

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response>;

/** Подменяет fetch: обработчик по «МЕТОД путь» (путь без /api/v1). Неописанный запрос — 404. */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost');
    const key = `${init.method ?? 'GET'} ${url.pathname.replace(/^\/api\/v1/, '')}`;
    const handler = handlers[key];
    return handler ? handler(url, init) : jsonResponse(404, { code: 'NOT_FOUND', retryable: false });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** Приложение целиком на роутере в памяти, начиная с адреса path. */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  const result = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...result, router, queryClient };
}
