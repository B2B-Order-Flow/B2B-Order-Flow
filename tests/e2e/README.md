# tests/e2e

Тесты на Playwright (TypeScript). Три набора:

| Проект     | Что проверяет                                                                                                                    | Нужен ли стенд                                              |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `fixtures` | Тестовые организации: контрольные суммы ИНН и ОГРН, уникальность                                                                 | Нет                                                         |
| `mocks`    | Контракты трёх моков из `docs/system/integrations.md`: ответы по ИНН, 404 в формате `problem+json`, `X-Correlation-Id`, сбои CRM | Моки с открытыми портами                                    |
| `api`      | Сквозные сценарии SC и EC из `docs/qa/qa-strategy.md`                                                                            | Весь стенд (SC-01 шаги 1–3 рабочие, остальное `test.fixme`) |

## Запуск

Нужен Node.js 22. Команды из папки `tests/e2e`.

```bash
npm ci
npm run test:fixtures      # без стенда
```

Для `mocks` сначала поднимите моки с открытыми портами (из корня репозитория):

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.mocks-ports.yaml up -d mock-crm mock-billing mock-registry
cd tests/e2e && npm run test:mocks
```

Для `api` нужен стенд с профилем DEMO и демо-пользователями (папка `db/demo` ядра, список в `infra/seed/README.md`). Из корня репозитория:

```bash
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.app-ports.yaml -f infra/compose.demo.yaml up -d --build --wait
cd tests/e2e
API_URL=http://localhost:8000 DEMO_PASSWORD=<пароль демо-пользователей> npm run test:api
```

Пока Nginx не добавлен в Compose, `API_URL` указывает на шлюз (порт 8000). Пароль демо-пользователей общий, его значение в репозиторий не кладём.

Адреса задаются переменными `MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL` (по умолчанию `localhost:8081`, `8082`, `8083`) и `API_URL` для сквозных тестов.

Отчёт: `npx playwright show-report`.

## Устройство

| Путь                                         | Что внутри                                                                                                            |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `fixtures/organizations.ts`                  | Таблица организаций из `integrations.md`, раздел 5. Меняется вместе с документом и стабами в `mocks/`                 |
| `lib/`                                       | Адреса, проверка ИНН и ОГРН, вспомогательные функции для моков и для API (`api.ts`: вход, создание и отправка заявки) |
| `tests/fixtures`, `tests/mocks`, `tests/api` | Сами тесты                                                                                                            |

Тесты сбоев «Шаткого» сбрасывают сценарии WireMock через Admin API перед каждым прогоном. Тест «Таймаут» ждёт 2 секунды, «Медленный» 1,5 секунды.
