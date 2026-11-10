package com.b2borderflow.core.error;

import org.springframework.http.HttpStatus;

/**
 * Каталог машинных кодов ошибок (docs/system/api-contract.md, 3.1).
 * Коды, добавленные ядром до обновления каталога, помечены комментарием.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Ошибка валидации", false),
    ACTIVE_ROLE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Не выбрана активная роль", false),
    ACTIVE_COMPANY_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Не определена компания пользователя", false),
    COMPANY_NOT_CONFIRMED(HttpStatus.UNPROCESSABLE_ENTITY, "Компания не подтверждена", false),
    COMPANY_INN_EXISTS(HttpStatus.CONFLICT, "Компания с таким ИНН уже существует", false),
    COMPANY_INN_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "ИНН не найден в справочнике", false),
    EXTERNAL_SYSTEM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Внешняя система недоступна", true),
    ILLEGAL_TRANSITION(HttpStatus.CONFLICT, "Переход недопустим из текущего статуса", false),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Версия объекта устарела", false),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Ключ идемпотентности использован с другим телом", false),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Объект не найден", false),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Действие не разрешено", false),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль", false),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Требуется аутентификация", false),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Некорректный запрос", false),
    CONFLICT(HttpStatus.CONFLICT, "Конфликт данных", false),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Метод не поддерживается", false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка", false);

    private final HttpStatus status;
    private final String title;
    private final boolean retryable;

    ErrorCode(HttpStatus status, String title, boolean retryable) {
        this.status = status;
        this.title = title;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** Идентификатор типа проблемы для поля {@code type} в RFC 9457. */
    public String getTypeSlug() {
        return name().toLowerCase().replace('_', '-');
    }
}
