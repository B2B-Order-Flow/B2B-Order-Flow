import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockApi } from '../test/utils';
import { apiRequest, buildQuery, configureApi, newIdempotencyKey } from './client';
import { ApiError } from './errors';

afterEach(() => {
  configureApi({
    getToken: () => null,
    getActiveRole: () => null,
    onUnauthorized: () => {},
    onAccessDenied: () => {},
  });
});

describe('apiRequest', () => {
  it('добавляет токен, активную роль и ключ идемпотентности', async () => {
    configureApi({ getToken: () => 'jwt-token', getActiveRole: () => 'SALES_MANAGER' });
    const fetchMock = mockApi({ 'POST /requests': () => jsonResponse(201, { id: 'r1' }) });

    await apiRequest('POST', '/requests', { body: { address: 'Москва' }, idempotencyKey: 'key-1' });

    const [url, init] = fetchMock.mock.calls[0]!;
    const headers = new Headers(init?.headers);
    expect(String(url)).toBe('/api/v1/requests');
    expect(headers.get('Authorization')).toBe('Bearer jwt-token');
    expect(headers.get('X-Active-Role')).toBe('SALES_MANAGER');
    expect(headers.get('Idempotency-Key')).toBe('key-1');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(init?.body).toBe(JSON.stringify({ address: 'Москва' }));
  });

  it('разбирает problem+json в ApiError с кодом, violations и correlationId', async () => {
    mockApi({
      'POST /requests/r1/submit': () =>
        jsonResponse(422, {
          code: 'VALIDATION_FAILED',
          title: 'Заявка заполнена не полностью',
          correlationId: 'corr-1',
          retryable: false,
          violations: [{ field: 'address', code: 'REQUIRED', message: 'Укажите адрес подключения' }],
        }),
    });

    const error = (await apiRequest('POST', '/requests/r1/submit', { body: { version: 1 } }).catch(
      (e) => e,
    )) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 422, code: 'VALIDATION_FAILED', correlationId: 'corr-1' });
    expect(error.violations).toEqual([{ field: 'address', code: 'REQUIRED', message: 'Укажите адрес подключения' }]);
  });

  it('по статусу без problem+json подставляет код (ответ Nginx 502)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () => new Response('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }),
      ),
    );

    const error = await apiRequest('GET', '/requests').catch((e) => e);

    expect(error).toMatchObject({ status: 502, code: 'CORE_UNAVAILABLE', retryable: true });
  });

  it('сетевой сбой превращает в NETWORK_ERROR с retryable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );

    const error = await apiRequest('GET', '/requests').catch((e) => e);

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', retryable: true, outcomeUnknown: true });
  });

  it('401 на защищённом запросе завершает сессию, а неверный пароль — нет', async () => {
    const onUnauthorized = vi.fn();
    configureApi({ onUnauthorized });
    mockApi({
      'GET /me': () => jsonResponse(401, { code: 'UNAUTHORIZED', retryable: false }),
      'POST /auth/login': () => jsonResponse(401, { code: 'INVALID_CREDENTIALS', retryable: false }),
    });

    await apiRequest('POST', '/auth/login', { body: {} }).catch(() => undefined);
    expect(onUnauthorized).not.toHaveBeenCalled();

    await apiRequest('GET', '/me').catch(() => undefined);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('ответ 204 без тела возвращает undefined', async () => {
    mockApi({ 'DELETE /requests/r1': () => new Response(null, { status: 204 }) });
    await expect(apiRequest('DELETE', '/requests/r1', { query: { version: 3 } })).resolves.toBeUndefined();
  });
});

describe('buildQuery', () => {
  it('пропускает пустые значения и повторяет массивы', () => {
    expect(buildQuery({ page: 0, size: 20, status: undefined, q: '', unassigned: true, ids: ['a', 'b'] })).toBe(
      '?page=0&size=20&unassigned=true&ids=a&ids=b',
    );
    expect(buildQuery({})).toBe('');
  });
});

describe('newIdempotencyKey', () => {
  it('даёт UUID v4 и без crypto.randomUUID (стенд по HTTP)', () => {
    const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    expect(newIdempotencyKey()).toMatch(uuidV4);

    vi.spyOn(crypto, 'randomUUID').mockImplementation(undefined as never);
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    expect(newIdempotencyKey()).toMatch(uuidV4);
  });
});
