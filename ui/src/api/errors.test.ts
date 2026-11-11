import { describe, expect, it } from 'vitest';
import { texts } from '../texts/ru';
import { ApiError, errorMessage, fieldErrors } from './errors';

describe('errorMessage', () => {
  it('берёт текст по коду из словаря', () => {
    const error = new ApiError({ status: 409, code: 'VERSION_CONFLICT' });
    expect(errorMessage(error)).toBe(texts.errors.VERSION_CONFLICT);
  });

  it('для неизвестного кода показывает title из ответа, иначе общий текст', () => {
    expect(errorMessage(new ApiError({ status: 409, code: 'SOMETHING_NEW', title: 'Новая ошибка' }))).toBe(
      'Новая ошибка',
    );
    expect(errorMessage(new ApiError({ status: 409, code: 'SOMETHING_NEW' }))).toBe(texts.errorFallback);
    expect(errorMessage(new Error('boom'))).toBe(texts.errorFallback);
  });

  it('для изменяющего действия при таймауте сообщает, что исход неизвестен', () => {
    const timeout = new ApiError({ status: 504, code: 'CORE_TIMEOUT', retryable: true });
    expect(errorMessage(timeout)).toBe(texts.errors.CORE_TIMEOUT);
    expect(errorMessage(timeout, { mutation: true })).toBe(texts.errorOutcomeUnknown);
  });
});

describe('ApiError', () => {
  it('показывает correlationId только для 5xx и 400', () => {
    expect(new ApiError({ status: 500, code: 'INTERNAL_ERROR', correlationId: 'c' }).showCorrelationId).toBe(true);
    expect(new ApiError({ status: 400, code: 'MALFORMED_REQUEST', correlationId: 'c' }).showCorrelationId).toBe(true);
    expect(new ApiError({ status: 409, code: 'VERSION_CONFLICT', correlationId: 'c' }).showCorrelationId).toBe(false);
    expect(new ApiError({ status: 500, code: 'INTERNAL_ERROR' }).showCorrelationId).toBe(false);
  });
});

describe('fieldErrors', () => {
  it('раскладывает violations по полям', () => {
    const error = new ApiError({
      status: 422,
      code: 'VALIDATION_FAILED',
      violations: [
        { field: 'address', code: 'REQUIRED', message: 'Укажите адрес подключения' },
        { field: '', code: 'X', message: 'Без поля' },
      ],
    });
    expect(fieldErrors(error)).toEqual({ address: 'Укажите адрес подключения' });
  });
});
