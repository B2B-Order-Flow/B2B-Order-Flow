import type { APIRequestContext } from '@playwright/test';
import { mocks } from './env';

export const crmUrl = (inn: string) => `${mocks.crm}/crm/v1/customers?inn=${inn}`;
export const billingUrl = (inn: string) => `${mocks.billing}/billing/v1/accounts/${inn}/balance`;
export const registryUrl = (inn: string) => `${mocks.registry}/registry/v1/organizations?inn=${inn}`;

// Сценарий WireMock у «Шаткого» хранит счётчик запросов на весь контейнер,
// поэтому перед каждым прогоном его сбрасываем.
export async function resetCrmScenarios(request: APIRequestContext): Promise<void> {
  const res = await request.post(`${mocks.crm}/__admin/scenarios/reset`);
  if (!res.ok()) throw new Error(`Не удалось сбросить сценарии CRM: ${res.status()}`);
}

let counter = 0;
export const correlationId = (prefix = 'e2e'): string => `${prefix}-${Date.now()}-${++counter}`;
