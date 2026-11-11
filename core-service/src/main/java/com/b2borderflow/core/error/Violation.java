package com.b2borderflow.core.error;

/** Нарушение поля запроса для массива {@code violations} ответа 422. */
public record Violation(String field, String code, String message) {
}
