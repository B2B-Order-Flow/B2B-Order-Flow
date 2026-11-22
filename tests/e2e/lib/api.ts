import type { APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { apiUrl } from './env';

// Пароль у всех демо-пользователей общий (infra/seed/README.md)
export const demoPassword = process.env.DEMO_PASSWORD ?? '';

const url = (path: string) => `${apiUrl}/api/v1${path}`;

export interface Stage {
  id: string;
  type: string;
  status: string;
  version: number;
}

export interface Req {
  id: string;
  status: string;
  version: number;
  managerId?: string; // клиенту не отдаётся: читать токеном оператора
  commitmentLevel?: number;
  integrationError?: boolean;
  closedAt?: string;
  stages?: Stage[];
}

export async function login(request: APIRequestContext, loginName: string) {
  if (!demoPassword) throw new Error('Не задана переменная DEMO_PASSWORD');
  const res = await request.post(url('/auth/login'), {
    data: { login: loginName, password: demoPassword },
  });
  if (!res.ok()) throw new Error(`Вход ${loginName}: ${res.status()}`);
  const body = await res.json();
  return { token: body.accessToken as string, userId: body.user.id as string };
}

export const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

// Желаемая дата: сегодня + n дней, контракт требует «не раньше сегодняшней»
const inDays = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10);

type DraftOptions = {
  address?: string;
  requestedSpeedMbps?: number;
  contactName?: string;
  contractPreference?: 'OFFER' | 'STANDARD' | 'CLIENT_FORM';
  clientComment?: string;
};

// Каждый тест создаёт свою заявку: тесты не зависят друг от друга
export async function createDraft(request: APIRequestContext, token: string, options: DraftOptions = {}): Promise<Req> {
  const res = await request.post(url('/requests'), {
    headers: { ...auth(token), 'Idempotency-Key': randomUUID() },
    data: {
      address: 'г. Екатеринбург, ул. Ленина, 1',
      requestedSpeedMbps: 100,
      desiredDate: inDays(30),
      contactName: 'Соколов А. В.',
      contactPhone: '+79990000001',
      contractPreference: 'OFFER',
      ...options,
    },
  });
  if (res.status() !== 201) throw new Error(`Создание черновика: ${res.status()}`);
  return res.json();
}

// Действие над заявкой или этапом: ответ 200 или понятная ошибка с телом
export async function act<T = Req>(
  request: APIRequestContext,
  token: string,
  path: string,
  data: Record<string, unknown>,
): Promise<T> {
  // submit ждёт опроса CRM, биллинга и справочника, поэтому таймаут с запасом
  const res = await request.post(url(path), { headers: auth(token), data, timeout: 15_000 });
  if (!res.ok()) throw new Error(`POST ${path}: ${res.status()} ${await res.text()}`);
  return res.json();
}

export async function getRequest(request: APIRequestContext, token: string, id: string): Promise<Req> {
  const res = await request.get(url(`/requests/${id}`), { headers: auth(token) });
  if (!res.ok()) throw new Error(`GET /requests/${id}: ${res.status()}`);
  return res.json();
}

export function stageOf(req: Req, type: string): Stage {
  const stage = req.stages?.find((s) => s.type === type && s.status !== 'CANCELLED' && s.status !== 'SUPERSEDED');
  if (!stage) throw new Error(`В заявке нет этапа ${type}`);
  return stage;
}
