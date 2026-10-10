# Команда и роли

## Участники

| Участник | Роль | Зона ответственности |
|---|---|---|
| **Дима** | Тимлид + Системный аналитик | Домен, статусы, маршрутизация, SLA, архитектура, координация, ревью |
| **Егор** | Бизнес-аналитик + Фронтендер | Процесс, роли, сценарии, UI-требования, панель оператора |
| **Денис** | Бэкендер (домен) | Реализация домена, БД, маршрутизация, SLA, история |
| **Кирилл** | Бэкендер (API) | API, DTO, мапперы, интеграции, уведомления, OpenAPI, безопасность |
| **Салават** | DevOps + QA | Docker, CI/CD, mock-сервисы, QA-стратегия, интеграционные и E2E-тесты |

## Стек по ролям

### Дима

| Что | Технология |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 4.1.x |
| БД | PostgreSQL 16 |
| ORM | Spring Data JPA |
| Миграции | Flyway |
| API (контракты) | OpenAPI (SpringDoc) |
| Документация | Markdown |
| Диаграммы | Mermaid |
| Ревью | Git + GitHub PR |

### Егор

**Аналитика:**

| Что | Технология |
|---|---|
| Документация | Markdown |
| Диаграммы | Mermaid |

**Frontend:**

| Что | Технология |
|---|---|
| UI | React |
| Сборка | Vite |
| HTTP-клиент | Axios |
| Состояние | React Context / Redux Toolkit |
| Роутинг | React Router |
| UI-компоненты | Material UI / Ant Design |
| Тесты | React Testing Library |

### Денис

| Что | Технология |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 4.1.x |
| Домен | Spring Data JPA |
| БД | PostgreSQL 16 |
| Миграции | Flyway |
| Валидация | Jakarta Validation |
| SLA | Spring `@Scheduled` + `Clock` |
| История | Spring Data JPA |
| Тесты | JUnit 5 + Testcontainers |

### Кирилл

| Что | Технология |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 4.1.x |
| API | REST + SpringDoc (OpenAPI) |
| DTO | Java Records / Lombok |
| Мапперы | MapStruct |
| Валидация | Jakarta Validation |
| Безопасность | Spring Security + JWT + BCrypt |
| Интеграции | RestTemplate / WebClient |
| Retry | Spring Retry |
| Mock-клиенты | WireMock |
| Уведомления | Spring Events |
| Тесты | JUnit 5 + Testcontainers |

### Салават

**DevOps:**

| Что | Технология |
|---|---|
| Контейнеры | Docker |
| Оркестрация | Docker Compose |
| CI/CD | GitHub Actions |
| Сервер | VPS на 2 месяца |
| Мониторинг | Spring Actuator |
| Логи | SLF4J + Logback |
| `.gitattributes` | LF |

**QA:**

| Что | Технология |
|---|---|
| Unit-тесты | JUnit 5 |
| Интеграционные | Testcontainers |
| E2E-тесты | Playwright / Selenium |
| Mock-сервисы | WireMock |
| Покрытие | JaCoCo |

## Стыки

| Пара | Что обсуждают |
|---|---|
| Дима ↔ Егор | Процесс, роли, сценарии, домен |
| Дима ↔ Денис | Домен, маршрутизация, SLA |
| Денис ↔ Кирилл | DTO, интеграция домена и API |
| Кирилл ↔ Егор | API и UI, контракты |
| Салават ↔ все | Инфра, тесты, mock |

## Правила работы

- Перекрёстное ревью: 1 аппрув, автор не мержит сам
- Дима — арбитр при спорах
- Владелец модуля — финальное слово по своему модулю
- Не лезть в чужой модуль без согласования