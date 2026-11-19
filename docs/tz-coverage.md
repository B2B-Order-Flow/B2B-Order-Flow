# Покрытие ТЗ: задачи, критерии приёмки, артефакты

## 1. Назначение

Сквозная матрица: каждая задача и каждый критерий приёмки из [ТЗ](tz.md) сопоставлены с документом, где решение описано, и с компонентом, где оно реализуется. Документ ведётся до защиты и служит чек-листом готовности.

Состояние: **✅ готово**, **🟡 частично**, **⬜ не начато**. Отдельно для аналитики (описано) и реализации (работает в коде).

Обновляется при каждом закрытии задачи. Состояние на **17.11.2026**, неделя 2 в работе ([plan-week2.md](plan-week2.md), [plan-week3.md](plan-week3.md)).

## 2. Задачи ТЗ (19)

| № | Задача | Где описано | Где реализуется | Аналитика | Реализация | Кто |
|---|---|---|---|---|---|---|
| 1 | Изучить бизнес-сценарий и описать процесс от создания до исполнения | [business-process.md](business/business-process.md), [research-b2b.md](business/research-b2b.md) | — | ✅ | — | бизнес-аналитик, руководитель |
| 2 | Определить роли участников и зоны ответственности | [roles.md](business/roles.md) | Identity & Access | ✅ | 🟡 вход по JWT, активная роль, роли компании и оператора | бизнес-аналитик, руководитель |
| 3 | Спроектировать доменную и статусную модель | [domain-model.md](system/domain-model.md), [status-model.md](system/status-model.md) | Ядро: миграции БД, сущности, FSM | ✅ | 🟡 миграции V1–V3, сущности, переходы T01–T03b | руководитель, разработчик ядра, разработчик API (схема БД) |
| 4 | Определить правила формирования маршрута | [routing-rules.md](system/routing-rules.md) | Ядро: FSM Router | ✅ | ⬜ | руководитель, разработчик ядра |
| 5 | Предусмотреть несколько сценариев обработки | [scenarios.md](business/scenarios.md) (SC-01…SC-03) | E2E-тесты | ✅ | 🟡 каркас Playwright, контрактные тесты моков, заготовка SC-01 | бизнес-аналитик, QA |
| 6 | Спроектировать механизм согласования между подразделениями | [business-process.md](business/business-process.md) §7, [status-model.md](system/status-model.md) | Ядро: Approval Engine | ✅ | ⬜ | руководитель, разработчик ядра |
| 7 | Выбрать архитектурный подход и обосновать выделение компонентов | [architecture.md](architecture/architecture.md), [system-design.md](architecture/system-design.md) | — | ✅ | — | руководитель |
| 8 | Реализовать основные компоненты системы | [system-design.md](architecture/system-design.md) §3 | `core-service`, `bff-gateway`, `ui` | ✅ | 🟡 ядро и шлюз недели 1, контракты и шлюз недели 2, каркас интерфейса | разработчик ядра, разработчик API, фронтенд |
| 9 | Реализовать единую точку взаимодействия (API Gateway) | [architecture.md](architecture/architecture.md) §2, [api-contract.md](system/api-contract.md) | Nginx + `bff-gateway` | ✅ | 🟡 `bff-gateway` проксирует операции недель 1–2; Nginx не подключён | разработчик API, DevOps и QA |
| 10 | Реализовать взаимодействие с mock-сервисами | [integrations.md](system/integrations.md) | Ядро: Integration Gateway, `mocks/` | ✅ | ✅ проверка данных заявки и ИНН при регистрации через три мока, повторы и таймауты | разработчик ядра, DevOps |
| 11 | Реализовать контроль SLA для отдельных этапов | [sla.md](system/sla.md) | Ядро: SLA Monitor | ✅ | ⬜ | руководитель, разработчик ядра |
| 12 | Реализовать уведомления участников | `system/notifications.md`, [api-contract.md](system/api-contract.md) §7 | Ядро: Notification Publisher; BFF: WebSocket | ✅ [notifications.md](system/notifications.md): матрица получателей, тексты, сигнал WebSocket | ⬜ | руководитель, разработчик API |
| 13 | Реализовать историю изменений заявки и действий | [domain-model.md](system/domain-model.md) §4.7, [status-model.md](system/status-model.md) | Ядро: Audit & History | ✅ | 🟡 `audit_log` переходов и `GET /requests/{id}/history`; ревизии — неделя 3 | руководитель, разработчик ядра |
| 14 | Разработать веб-панель оператора или другой интерфейс | `business/ui-requirements.md` | `ui` | 🟡 [ui-requirements.md](business/ui-requirements.md) — экраны недель 1–2; макеты в Figma | 🟡 каркас и вход в `dev`; экраны недель 1–2 в работе | бизнес-аналитик |
| 15 | Описать API и контракты взаимодействия | [api-contract.md](system/api-contract.md), [integrations.md](system/integrations.md) | OpenAPI-файлы в репозитории | ✅ | 🟡 `core-api.yaml` и `external-api.yaml` на недели 1–2, сверка в CI | разработчик API, разработчик ядра |
| 16 | Настроить логирование и базовую обработку ошибок | `architecture/logging.md`, [api-contract.md](system/api-contract.md) §3 | Logback, обработчики ошибок | ✅ формат ошибок и логирование описаны | 🟡 ошибки RFC 9457, `correlationId` и JSON-логи в ядре; единый формат ошибок в шлюзе | DevOps, разработчик API |
| 17 | Покрыть ключевые бизнес-сценарии автотестами | `qa/qa-strategy.md`, [sla.md](system/sla.md) §11, [integrations.md](system/integrations.md) §9 | JUnit, Testcontainers, Playwright, pytest | ✅ стратегия тестирования описана | 🟡 интеграционные тесты ядра (Testcontainers, WireMock), pytest шлюза, Vitest интерфейса, контрактные тесты моков; CI на каждый PR | DevOps и QA |
| 18 | Подготовить README и инструкцию по запуску | `README.md` (корень, ветка `dev`) | — | ✅ корневой `README.md` и README сервисов в `dev` | 🟡 запуск через Docker Compose описан; интерфейс и Nginx в Compose не входят | DevOps и QA |
| 19 | Подготовить и провести демонстрацию сквозного сценария | [scenarios.md](business/scenarios.md) §10 | Сид демо-данных | ✅ сценарий есть | 🟡 профиль `DEMO` и список демо-пользователей; сид — нет | руководитель и вся команда |

## 3. Критерии приёмки (14)

| Критерий | Чем закрывается | Состояние |
|---|---|---|
| Описан конкретный бизнес-сценарий обработки заявки | [business-process.md](business/business-process.md), [scenarios.md](business/scenarios.md) | ✅ |
| Определены роли участников и зоны ответственности | [roles.md](business/roles.md), матрица действий и RACI | ✅ |
| Разработана статусная модель и логика переходов | [status-model.md](system/status-model.md): 13 статусов, 26 переходов | ✅ |
| Реализован механизм маршрутизации и согласования по параметрам | [routing-rules.md](system/routing-rules.md): R-01…R-08 | описано, код ⬜ |
| Предусмотрены варианты маршрута для стандартных и нестандартных заявок | SC-01…SC-03, EC-03, EC-04 | описано, код ⬜ |
| Реализован контроль SLA и уведомления о сроках | [sla.md](system/sla.md) | описано, код ⬜ |
| Сохраняется история изменений и прохождения по этапам | `audit_log`, `request_revision` | описано, код 🟡: журнал переходов |
| Реализовано взаимодействие с mock-сервисами | [integrations.md](system/integrations.md): три системы | ✅ |
| Обоснованы архитектурный подход и технические решения | [architecture.md](architecture/architecture.md), [system-design.md](architecture/system-design.md) | ✅ |
| Реализована базовая обработка ошибок и логирование | [api-contract.md](system/api-contract.md) §3, [logging.md](architecture/logging.md) | описано, код 🟡 |
| Предусмотрены автотесты ключевых сценариев | [qa-strategy.md](qa/qa-strategy.md) | описано, код 🟡: тесты модулей, сквозные — заготовки |
| Подготовлены README и инструкция по запуску | Корневой `README.md` написан в ветке `dev` | 🟡 |
| Система запускается на собственной инфраструктуре | `infra/`: Docker Compose, Nginx | 🟡 Docker Compose: БД, три мока, ядро, шлюз; Nginx и интерфейс — нет |
| Проведена публичная защита с демонстрацией сквозного сценария | [scenarios.md](business/scenarios.md) §10 | ⬜ |

## 4. Сверх ТЗ

Решения, которых ТЗ не требует. На защите подаются как продуктовая глубина, но приоритет у них ниже, чем у пунктов разделов 2 и 3.

| Что | Где описано | Состояние |
|---|---|---|
| Регистрация компаний-клиентов и подтверждение администратором | [roles.md](business/roles.md) §4, SC-00 | ✅ описано, реализовано в ядре (неделя 1) |
| Клиент сам подаёт заявки и следит за ними | [business-process.md](business/business-process.md) | ✅ описано |
| Отмена по уровням обязательств с компенсацией затрат | [business-process.md](business/business-process.md) §11 | ✅ описано |
| Правила паузы SLA: справочник, журнал, предел, два времени | [sla.md](system/sla.md) §6 | ✅ описано |
| Архив заявок и экспорт для аналитиков | [archive-export.md](system/archive-export.md) | ✅ описано |
| Несколько вариантов услуги на одну заявку | [domain-model.md](system/domain-model.md) (`service_offer`) | 🟡 крючок в модели; процесс — бизнес-аналитик до 22.11, системная часть — до 25.11, реализация — неделя 4 |
| Права внутри заявки (`request_member`) | [domain-model.md](system/domain-model.md) | 🟡 крючок в модели, процесс не описан |
| Нефункциональные требования | [nfr.md](system/nfr.md) | ✅ описано |

## 5. Что мешает закрыть покрытие

| Пробел | Последствие | Кто |
|---|---|---|
| Миграции недели 2 (SLA, уведомления, `routing_config`, справочники) и демо-пользователи `db/demo` не сделаны | Задачи 11, 12 и 19 не начаты в коде: SLA Monitor и уведомления в ядре ждут таблиц | разработчик API (схема БД) |
| Nginx и интерфейс не входят в Docker Compose | Критерий «запуск на собственной инфраструктуре» закрыт частично | DevOps и QA |
| Экраны недель 1–2 не влиты в `dev` | Задача 14 закрыта только каркасом | фронтенд |
