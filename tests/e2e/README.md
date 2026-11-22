# tests/e2e

Тесты на Playwright (TypeScript). Три набора:

| Проект     | Что проверяет                                                                                                                    | Нужен ли стенд                                              |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `fixtures` | Тестовые организации: контрольные суммы ИНН и ОГРН, уникальность                                                                 | Нет                                                         |
| `mocks`    | Контракты трёх моков из `docs/system/integrations.md`: ответы по ИНН, 404 в формате `problem+json`, `X-Correlation-Id`, сбои CRM | Моки с открытыми портами                                    |
| `api`      | SC-01 от черновика до закрытия, шаги 1–10. Остальные сценарии помечены `test.fixme`                                                | Стенд DEMO, запросы через Nginx                             |

## Запуск

Нужен Node.js 22. Команды из папки `tests/e2e`.

```bash
npm ci
npm run test:fixtures      # без стенда
```

Для `mocks` подготовь `.env` по [инструкции запуска](../../infra/README.md#запуск) и подними моки с открытыми портами из корня репозитория.

```bash
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.mocks-ports.yaml up -d mock-crm mock-billing mock-registry
cd tests/e2e && npm run test:mocks
```

### Сквозные тесты API

Подготовь `.env` по [инструкции запуска](../../infra/README.md#запуск), затем подними [стенд DEMO](../../infra/README.md#стенд-demo) и запусти тест. Команды выполняются из корня репозитория.

```bash
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.demo.yaml up -d --build --wait
cd tests/e2e
npm ci
read -rs -p 'Пароль демо-пользователей: ' demo_password
printf '\n'
DEMO_PASSWORD="$demo_password" npm run test:api
unset demo_password
```

`API_URL` по умолчанию указывает на `http://localhost`. Если в `.env` задан, например, `HTTP_PORT=8090`, выполни `export API_URL=http://localhost:8090` перед запуском теста. Тест читает общий пароль из `DEMO_PASSWORD`. Без него SC-01 пропускается. Тест сам создаёт заявку. [Демо-пользователи и компании](../../infra/seed/README.md)

Полный прогон SC-01 требует реализации переходов ядра из [PR #54](https://github.com/B2B-Order-Flow/B2B-Order-Flow/pull/54). В GitHub Actions сейчас запускаются `fixtures` и `mocks`. Для `mocks` адреса можно переопределить через `MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL` (по умолчанию `localhost:8081`, `8082`, `8083`).

Отчёт: `npx playwright show-report`.

## Устройство

| Путь                                         | Что внутри                                                                                                            |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `fixtures/organizations.ts`                  | Таблица организаций из `integrations.md`, раздел 5. Меняется вместе с документом и стабами в `mocks/`                 |
| `lib/`                                       | Адреса, проверка ИНН и ОГРН, вспомогательные функции для моков и для API (`api.ts`: вход, создание и отправка заявки) |
| `tests/fixtures`, `tests/mocks`, `tests/api` | Сами тесты                                                                                                            |

Тесты сбоев «Шаткого» сбрасывают сценарии WireMock через Admin API перед каждым прогоном. Тест «Таймаут» ждёт 2 секунды, «Медленный» 1,5 секунды.
