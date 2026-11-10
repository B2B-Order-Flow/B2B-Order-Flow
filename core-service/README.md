# core-service

Транзакционное ядро B2B-Order-Flow: Java 21, Spring Boot 4.1.x, PostgreSQL 16, Flyway.
Владелец: Кирилл. Внутренний OpenAPI-контракт — `src/main/resources/openapi/core-api.yaml`.

## Что сделано в неделе 1

Вертикальный срез «создание заявки клиентом → проверка данных → взятие менеджером»:

- вход по логину/паролю, выдача JWT, активная роль `X-Active-Role`, отдельный
  межсервисный токен `X-Internal-Token`;
- регистрация компании (локальная проверка ИНН + mock-справочник), подтверждение и
  отклонение компании администратором;
- черновик заявки, редактирование и мягкое удаление, автоматическое предложение услуги
  (`service_offer`, ровно одно и в статусе `SELECTED`);
- отправка заявки (T01): проверки, ревизия №1, `DATA_CHECK`, аудит; три внешних вызова
  вне транзакции; T02 при сбое (EC-10) и T03/T03b при успехе;
- `claim` менеджером и `assign-manager` координатором (EC-19), включая переназначение;
- `recheck-data` координатором: повторяются только недостающие системы;
- аудит каждого перехода, идемпотентность создания, единая политика доступа по компании
  (EC-16), `testcontainers`-тесты и негативный тест доступа.

SLA-таймеры, уведомления, маршрутизация T04+, этапы и фоновые повторы — неделя 2
(по `docs/plan-week1.md`); в схеме и домене для них оставлены колонки и сущности.

## Версии

Версии проверены сборкой и тестами (`mvn verify`):

| Компонент | Версия | Комментарий |
|---|---|---|
| Java | 21 | `java.version` в `pom.xml` |
| Spring Boot | 4.1.2 | Spring Framework 7, Hibernate 7.4, Spring Security 7.1 |
| OpenAPI Generator | 7.27.0 | генерация серверных интерфейсов из YAML |
| Flyway | 12.4.0 | `spring-boot-starter-flyway`, отдельный пользователь-мигратор |
| PostgreSQL | 16 | `postgres:16-alpine` в Testcontainers |
| Testcontainers | 2.0.5 | по `docs/qa/qa-strategy.md`: 1.x несовместим с Docker Engine 29 |
| WireMock | 3.13.2 | in-process в интеграционных тестах |

Если команда зафиксирует другую версию Spring Boot, менять её нужно здесь и в `pom.xml`
одним PR; причины решения — в разделе «Отступления от документов».

## Сборка и тесты

Нужны JDK 21 и Docker (для Testcontainers). Maven можно не устанавливать — есть wrapper.

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

На Windows: `mvnw.cmd clean verify`.

Интеграционные тесты поднимают PostgreSQL 16 и WireMock в Testcontainers, поэтому
Docker должен быть запущен. Тесты не требуют заранее подготовленной БД.

## Контракт API

`core-api.yaml` — единственный источник истины. Сборка генерирует интерфейсы
`com.b2borderflow.core.api.*Api` (в `target/generated-sources/openapi`), контроллеры их
реализуют. Изменение эндпоинта: правится YAML, затем реализация; вручную API в коде
не описывается.

Проверка контракта без запуска приложения:

```bash
./mvnw generate-sources
```

Заголовки: `X-Internal-Token` обязателен для всех `/internal/v1/**`; `Authorization: Bearer`
и `X-Active-Role` — для защищённых операций. `X-Correlation-Id` принимается, генерируется
при отсутствии, возвращается в ответе, пишется в `audit_log` и передаётся в моки.

## Конфигурация

| Переменная | Назначение |
|---|---|
| `CORE_DB_URL` | JDBC URL; по умолчанию `jdbc:postgresql://localhost:5432/core_db` |
| `CORE_DB_NAME` | имя БД в составе URL по умолчанию |
| `CORE_DB_APP_USER`, `CORE_DB_APP_PASSWORD` | пользователь приложения (только данные) |
| `CORE_DB_MIGRATION_USER`, `CORE_DB_MIGRATION_PASSWORD` | владелец схемы для Flyway |
| `JWT_SECRET` | HMAC-секрет JWT, не короче 32 символов; обязателен |
| `INTERNAL_TOKEN` | общий секрет BFF → ядро; обязателен |
| `MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL` | адреса mock-сервисов |
| `SERVER_PORT` | порт ядра, по умолчанию 8080 |

В Docker Compose адреса моков — `http://mock-crm:8080`, `http://mock-billing:8080`,
`http://mock-registry:8080`; БД — `jdbc:postgresql://core-db:5432/${CORE_DB_NAME}`.
Порт БД наружу в базовом compose не публикуется.

## Миграции и схема

`src/main/resources/db/migration/`:

- `V1__companies_users_roles.sql` — пользователи, компании, членство и роли;
- `V2__requests_audit.sql` — заявка, предложение услуги, этапы, ревизии, аудит,
  проверки внешних систем, ключи идемпотентности.

Схема соответствует `docs/system/domain-model.md`, раздел 8: `uuid` с `gen_random_uuid()`,
`timestamptz` в UTC, `text` + `CHECK` вместо enum-типов, `numeric(14,2)`, оптимистичная
блокировка, `search_vector` с GIN-индексом и триггером.

Миграция `audit_log` отзывает `UPDATE`/`DELETE`/`TRUNCATE` у пользователя приложения через
плейсхолдер `${app-user}`; роль создаёт `infra/postgres/init`, миграция её не создаёт.

> По разделу 8 плана владелец папки миграций — Денис. Чтобы ядро можно было собрать и
> проверить на `ddl-auto=validate` до слияния его PR, каркас схемы недели 1 подготовлен в
> этой ветке. Перед слиянием миграции нужно свести с PR Дениса; номера версий и состав
> таблиц — по договорённости, применённые миграции не переписываются.

Демо-пользователи и компании (`db/demo/`) в этой ветке не добавлены: по плану это
отдельные миграции профилей `DEMO`/тестового, их готовит Денис. Для ручного прогона
пользователь создаётся через `POST /internal/v1/auth/register`, роли оператора выдаются SQL.

## Структура

```
com.b2borderflow.core
├── api            — сгенерированные интерфейсы и модели (не править вручную)
├── auth           — регистрация, вход, JWT
├── company        — компании, членство, роли, подтверждение
├── user           — аккаунты
├── request        — заявки, workflow T01–T03b, доступ, представления
├── integration    — mock CRM, биллинг, справочник; повторы и таймауты
├── audit          — append-only журнал
├── idempotency    — ключи создания
├── security       — JWT, активная роль, внутренний токен
├── error          — RFC 9457, каталог кодов
├── logging        — correlationId, MDC
└── config         — свойства и бины
```

## Тесты

| Класс | Что проверяет |
|---|---|
| `AuthIntegrationTest` | регистрация/вход/`/me`, неверный пароль, отсутствие внутреннего токена, активная роль |
| `CompanyIntegrationTest` | SC-00: регистрация, подтверждение, отклонение, неверный ИНН, дубль ИНН |
| `RequestSubmissionIntegrationTest` | T01–T03b, EC-10 и повторная проверка, claim/assign, идемпотентность, обязательные поля, повторы |
| `RequestAccessIntegrationTest` | EC-16: чужая компания, аудит отказа, мягкое удаление |
| `InnValidatorTest` | контрольные суммы ИНН 10/12 цифр |
| `ContextLoadsTest` | Flyway + `ddl-auto=validate` на реальной схеме |

Тесты проверяют также, что `correlationId` доходит до мока и попадает в `audit_log`,
а в ответе-ошибке нет стека.

## Логи

`logback-spring.xml` пишет построчный JSON в stdout: `timestamp`, `level`, `service`,
`message`, `correlationId`, `userId`, `role`, при необходимости `requestId`. Тела ответов
внешних систем и секреты в лог не пишутся; сырые ответы лежат в `external_check`.

## Отступления от документов (для списка расхождений)

| Что | Почему |
|---|---|
| Spring Boot 4.1.2, Java 21 | выбранная версия проверена сборкой и тестами; контракт-генератор совместим |
| Testcontainers 2.0.5 | требование `qa-strategy.md`: 1.x не работает с Docker Engine 29 |
| `CONFLICT`, `MALFORMED_REQUEST`, `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `ACCESS_DENIED`, `ACTIVE_COMPANY_REQUIRED`, `EXTERNAL_SYSTEM_UNAVAILABLE` (503) | коды нужны границе сервиса, но пока не внесены в каталог `api-contract.md`; добавить в каталог при следующей правке документа |
| `DELETE /requests/{id}?version=N` | у `DELETE` нет тела; версия передана query-параметром |
| `PATCH /requests/{id}` и `DELETE /requests/{id}` | нужны форме заявки недели 1, хотя в перечне BFF на неделю не входили |
| `POST /requests/{id}/recheck-data` | EC-10 требует ручной повторной проверки координатором |
| `GET /requests/{id}/history` | карточка с историей нужна интерфейсу; данные уже пишутся в аудит |

Фоновые повторы EC-10 (6.5 `integrations.md`), контрольные сроки и уведомления
подключаются на неделе 2 вместе с таблицами SLA и `notification`.
