import { texts } from '../texts/ru';
import type { Violation } from './types';

// Ошибка API в едином виде. Интерфейс ветвится по code, а не по тексту (api-contract.md §3).
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title?: string;
  readonly detail?: string;
  readonly correlationId?: string;
  readonly retryable: boolean;
  readonly violations: Violation[];

  constructor(init: {
    status: number;
    code: string;
    title?: string;
    detail?: string;
    correlationId?: string;
    retryable?: boolean;
    violations?: Violation[];
  }) {
    super(init.title ?? init.code);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.correlationId = init.correlationId;
    this.retryable = init.retryable ?? false;
    this.violations = init.violations ?? [];
  }

  /** Ошибка связи или сервера, после которой исход изменяющего действия неизвестен. */
  get outcomeUnknown(): boolean {
    return this.code === 'CORE_TIMEOUT' || this.code === 'NETWORK_ERROR' || this.code === 'CORE_UNAVAILABLE';
  }

  /** Показывать ли пользователю correlationId для обращения. */
  get showCorrelationId(): boolean {
    return Boolean(this.correlationId) && (this.status >= 500 || this.status === 400);
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

// Код по HTTP-статусу, если тело не в формате problem+json (например, ответ Nginx).
function codeForStatus(status: number): { code: string; retryable: boolean } {
  if (status === 400) return { code: 'MALFORMED_REQUEST', retryable: false };
  if (status === 401) return { code: 'UNAUTHORIZED', retryable: false };
  if (status === 403) return { code: 'ACCESS_DENIED', retryable: false };
  if (status === 404) return { code: 'NOT_FOUND', retryable: false };
  if (status === 409) return { code: 'CONFLICT', retryable: false };
  if (status === 422) return { code: 'VALIDATION_FAILED', retryable: false };
  if (status === 429) return { code: 'TOO_MANY_REQUESTS', retryable: true };
  if (status === 502 || status === 503) return { code: 'CORE_UNAVAILABLE', retryable: true };
  if (status === 504) return { code: 'CORE_TIMEOUT', retryable: true };
  if (status >= 500) return { code: 'INTERNAL_ERROR', retryable: false };
  return { code: `HTTP_${status}`, retryable: false };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function parseViolations(value: unknown): Violation[] {
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord).map((v) => ({
    field: asString(v.field) ?? '',
    code: asString(v.code) ?? '',
    message: asString(v.message) ?? '',
  }));
}

/** Собирает ApiError из ответа с ошибкой: тело problem+json, иначе — по HTTP-статусу. */
export function errorFromResponse(status: number, body: unknown, headerCorrelationId?: string | null): ApiError {
  const fallback = codeForStatus(status);
  if (isRecord(body) && asString(body.code)) {
    return new ApiError({
      status,
      code: asString(body.code) ?? fallback.code,
      title: asString(body.title),
      detail: asString(body.detail),
      correlationId: asString(body.correlationId) ?? headerCorrelationId ?? undefined,
      retryable: typeof body.retryable === 'boolean' ? body.retryable : fallback.retryable,
      violations: parseViolations(body.violations),
    });
  }
  return new ApiError({
    status,
    code: fallback.code,
    correlationId: headerCorrelationId ?? undefined,
    retryable: fallback.retryable,
  });
}

/**
 * Текст ошибки для пользователя по ui-requirements.md §10.
 * mutation: true — для изменяющего действия при обрыве связи сообщаем, что исход неизвестен.
 */
export function errorMessage(error: unknown, options: { mutation?: boolean } = {}): string {
  if (!isApiError(error)) return texts.errorFallback;
  if (options.mutation && (error.code === 'CORE_TIMEOUT' || error.code === 'NETWORK_ERROR')) {
    return texts.errorOutcomeUnknown;
  }
  return texts.errors[error.code] ?? error.title ?? texts.errorFallback;
}

/** Ошибки полей формы из violations: { field: message }. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!isApiError(error)) return {};
  return Object.fromEntries(error.violations.filter((v) => v.field).map((v) => [v.field, v.message]));
}
