package com.b2borderflow.core.integration;

/** Сбой внешней системы, который имеет смысл повторить: 5xx, таймаут, недоступность. */
public class RetryableExternalException extends RuntimeException {

    private final int httpStatus;

    public RetryableExternalException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
