import { ApiError, errorFromResponse } from './errors';
import type { Role } from './types';

// Единый слой запросов к шлюзу (api-contract.md §2–5):
// - базовый путь /api/v1, JSON;
// - Authorization: Bearer и X-Active-Role — из сессии;
// - Idempotency-Key — для создающих запросов;
// - ошибки — ApiError с кодом из problem+json;
// - 401 на защищённом запросе завершает сессию.

export interface ApiConfig {
  baseUrl: string;
  getToken: () => string | null;
  getActiveRole: () => Role | null;
  onUnauthorized: () => void;
  onAccessDenied: () => void;
}

let config: ApiConfig = {
  baseUrl: '/api/v1',
  getToken: () => null,
  getActiveRole: () => null,
  onUnauthorized: () => {},
  onAccessDenied: () => {},
};

export function configureApi(partial: Partial<ApiConfig>): void {
  config = { ...config, ...partial };
}

type QueryValue = string | number | boolean | null | undefined | Array<string | number>;

export interface RequestOptions {
  body?: unknown;
  query?: Record<string, QueryValue>;
  idempotencyKey?: string;
  signal?: AbortSignal;
  /** Не завершать сессию на 401 (вход и регистрация). */
  skipAuthHandling?: boolean;
}

export function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)));
    else params.append(key, String(value));
  }
  const result = params.toString();
  return result ? `?${result}` : '';
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return undefined;
  const type = response.headers.get('Content-Type') ?? '';
  if (!type.includes('json')) return text;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function apiRequest<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: 'application/json, application/problem+json' });
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  const token = config.getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const role = config.getActiveRole();
  if (role) headers.set('X-Active-Role', role);
  if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey);

  let response: Response;
  try {
    response = await fetch(`${config.baseUrl}${path}${buildQuery(options.query)}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    // Сервер не ответил совсем: для пользователя это то же, что недоступность ядра
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', retryable: true });
  }

  const body = await readBody(response);
  if (!response.ok) {
    const error = errorFromResponse(response.status, body, response.headers.get('X-Correlation-Id'));
    if (error.status === 401 && error.code !== 'INVALID_CREDENTIALS' && !options.skipAuthHandling) {
      config.onUnauthorized();
    }
    if (error.status === 403 && error.code === 'ACCESS_DENIED') config.onAccessDenied();
    throw error;
  }
  return body as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => apiRequest<T>('GET', path, options),
  post: <T>(path: string, options?: RequestOptions) => apiRequest<T>('POST', path, options),
  patch: <T>(path: string, options?: RequestOptions) => apiRequest<T>('PATCH', path, options),
  put: <T>(path: string, options?: RequestOptions) => apiRequest<T>('PUT', path, options),
  delete: <T>(path: string, options?: RequestOptions) => apiRequest<T>('DELETE', path, options),
};

/**
 * Ключ идемпотентности (UUID v4). crypto.randomUUID есть только в защищённом контексте (HTTPS или localhost),
 * а стенд может открываться по HTTP — тогда собираем UUID из crypto.getRandomValues.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
