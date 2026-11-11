# infra

Docker Compose и скрипты инициализации БД. Конфигурация Nginx и резервное копирование появятся позже. Workflow GitHub Actions лежат в `.github/workflows/` в корне репозитория.

Владелец: Салават.

## Что сейчас в compose

| Сервис | Что это |
|---|---|
| `core-db` | PostgreSQL 16, база `core_db`. Порт наружу не открыт |
| `mock-crm`, `mock-billing`, `mock-registry` | WireMock, описание в `mocks/README.md`. Порты наружу не открыты |
| `core-service` | Ядро (Spring Boot, Java 21), образ собирается из `core-service/Dockerfile`. Порт наружу не открыт |
| `bff-gateway` | BFF (FastAPI), образ собирается из `bff-gateway/Dockerfile`. Порт наружу не открыт |

Nginx и UI добавятся отдельными PR, когда у сервисов появятся образы.

## Запуск

Из корня репозитория:

```
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d --build
docker compose --env-file .env -f infra/compose.yaml ps
```

В `.env` замени все `change_me` на свои значения. Если переменной нет или она пустая, compose не запустится и назовёт её; `change_me` он не проверяет. Остановить: `docker compose --env-file .env -f infra/compose.yaml down`. Флаг `-v` удалит том с БД.

Моки на localhost (для отладки и сквозных тестов с хоста) подключаются вторым файлом:

```
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.mocks-ports.yaml up -d
```

Порты: CRM `8081`, биллинг `8082`, справочник `8083`, только на `127.0.0.1`.

Шлюз на localhost (для сквозных тестов с хоста) и профиль DEMO подключаются своими файлами:

```
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.app-ports.yaml up -d
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.demo.yaml up -d
```

Шлюз открывается на `127.0.0.1:8000`. Профиль DEMO задаёт для ядра `SLA_PROFILE=DEMO` (нормативы SLA в минутах). Первая сборка ядра долгая: Maven скачивает зависимости.

## База данных

При первом запуске `postgres/init/01-roles.sh` создаёт две роли: `CORE_DB_MIGRATION_USER` (владелец схемы `public`, под ним идёт Flyway) и `CORE_DB_APP_USER` (права на данные, схему менять не может). Скрипт срабатывает только на пустом томе. Если меняешь имена или пароли в `.env`, пересоздай том: `down -v`.

Для Кирилла: ограничение `audit_log` (приложению только `INSERT` и `SELECT`) делает миграция при создании таблицы, например `REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM core_app`. Имя роли можно брать из плейсхолдера Flyway.

Адреса моков для ядра внутри сети compose: `http://mock-crm:8080`, `http://mock-billing:8080`, `http://mock-registry:8080` (`MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL`).
