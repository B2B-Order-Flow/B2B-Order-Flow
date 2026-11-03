# infra

Docker Compose, конфигурация Nginx, скрипты запуска и резервного копирования. Workflow GitHub Actions лежат в `.github/workflows/`.

Владелец: Салават.

## Что сейчас в compose

| Сервис | Что это |
|---|---|
| `core-db` | PostgreSQL 16, база `core_db`. Порт наружу не открыт |
| `mock-crm`, `mock-billing`, `mock-registry` | WireMock, описание в `mocks/README.md`. Порты наружу не открыты |

Nginx, BFF, ядро и UI добавятся отдельными PR, когда у сервисов появятся образы.

## Запуск

Из корня репозитория:

```
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

В `.env` замени все `change_me` на свои значения. Если переменной нет или она пустая, compose не запустится и назовёт её; `change_me` он не проверяет. Остановить: `docker compose --env-file .env -f infra/compose.yaml down`. Флаг `-v` удалит том с БД.

Моки на localhost (для отладки и сквозных тестов с хоста) подключаются вторым файлом:

```
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.mocks-ports.yaml up -d
```

Порты: CRM `8081`, биллинг `8082`, справочник `8083`, только на `127.0.0.1`.

## База данных

При первом запуске `postgres/init/01-roles.sh` создаёт две роли: `CORE_DB_MIGRATION_USER` (владелец схемы `public`, под ним идёт Flyway) и `CORE_DB_APP_USER` (права на данные, схему менять не может). Скрипт срабатывает только на пустом томе. Если меняешь имена или пароли в `.env`, пересоздай том: `down -v`.

Для Кирилла: ограничение `audit_log` (приложению только `INSERT` и `SELECT`) делает миграция при создании таблицы, например `REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM core_app`. Имя роли можно брать из плейсхолдера Flyway.

Адреса моков для ядра внутри сети compose: `http://mock-crm:8080`, `http://mock-billing:8080`, `http://mock-registry:8080` (`MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL`).
