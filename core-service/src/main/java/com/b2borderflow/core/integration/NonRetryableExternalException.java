package com.b2borderflow.core.integration;

/** Сбой внешней системы, который повторять бессмысленно: 4xx, кроме бизнес-404. */
public class NonRetryableExternalException extends RuntimeException {

    private final int httpStatus;

    public NonRetryableExternalException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
