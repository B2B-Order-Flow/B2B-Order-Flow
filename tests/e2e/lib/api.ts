import type { APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { apiUrl } from './env';

// Пароль у всех демо-пользователей общий (infra/seed/README.md)
const password = process.env.DEMO_PASSWORD ?? '';

export async function login(request: APIRequestContext, loginName: string): Promise<string> {
  if (!password) throw new Error('Не задана переменная DEMO_PASSWORD');
  const res = await request.post(`${apiUrl}/api/v1/auth/login`, {
    data: { login: loginName, password },
  });
  if (!res.ok()) throw new Error(`Вход ${loginName}: ${res.status()}`);
  return (await res.json()).accessToken;
}

export const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

// Каждый тест создаёт свою заявку: тесты не зависят друг от друга
export async function createDraft(request: APIRequestContext, token: string) {
  const res = await request.post(`${apiUrl}/api/v1/requests`, {
    headers: { ...auth(token), 'Idempotency-Key': randomUUID() },
    data: {
      address: 'г. Екатеринбург, ул. Ленина, 1',
      requestedSpeedMbps: 100,
      desiredDate: '2026-12-01',
      contactName: 'Соколов А. В.',
      contactPhone: '+79990000001',
      contractPreference: 'OFFER',
    },
  });
  if (res.status() !== 201) throw new Error(`Создание черновика: ${res.status()}`);
  return res.json();
}

export async function submit(request: APIRequestContext, token: string, id: string, version: number) {
  // Ответ приходит после опроса CRM, биллинга и справочника, поэтому таймаут с запасом
  return request.post(`${apiUrl}/api/v1/requests/${id}/submit`, {
    headers: auth(token),
    data: { version },
    timeout: 15_000,
  });
}
