package com.b2borderflow.core.routing;

/**
 * Производный класс заявки для карточки, фильтров и аналитики
 * (docs/system/routing-rules.md, раздел 8). Класс не управляет маршрутом:
 * он только описывает набор согласований, который уже выбран правилами.
 */
public enum RequestClass {

    STANDARD,
    EXTENDED,
    LARGE
}
