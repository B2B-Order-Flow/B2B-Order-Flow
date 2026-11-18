package com.b2borderflow.core.request;

/**
 * Результат проверки технической возможности (docs/system/routing-rules.md, раздел 5).
 * Вносится техническим специалистом на этапе {@code TECH_CHECK}; от него зависят
 * окончательный маршрут, этап «Стройка» и отказ по заявке.
 */
public enum TechResult {
    AVAILABLE,
    WITH_CONSTRUCTION,
    NONE
}
