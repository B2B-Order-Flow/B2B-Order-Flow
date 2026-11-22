# infra

Docker Compose и скрипты инициализации БД. Конфигурация Nginx лежит рядом с интерфейсом, в `ui/nginx/`. Workflow GitHub Actions лежат в `.github/workflows/` в корне репозитория.

Владелец: Салават.

## Что сейчас в compose

| Сервис | Что это |
|---|---|
| `core-db` | PostgreSQL 16, база `core_db`. Порт наружу не открыт |
| `backup` | Ежедневный дамп PostgreSQL под ролью `backup_ro`. По умолчанию хранит последние семь копий в томе `core-backups` |
| `mock-crm`, `mock-billing`, `mock-registry` | WireMock, описание в `mocks/README.md`. Порты наружу не открыты |
| `core-service` | Ядро (Spring Boot, Java 21), образ собирается из `core-service/Dockerfile`. Порт наружу не открыт |
| `bff-gateway` | BFF (FastAPI), образ собирается из `bff-gateway/Dockerfile`. Порт наружу не открыт |
| `nginx` | Единая точка входа: раздаёт интерфейс, проксирует `/api` и `/ws` в шлюз, ограничивает частоту запросов. Образ собирается из `ui/Dockerfile`. Наружу открыт только он: `127.0.0.1:80` |

После запуска интерфейс открывается на `http://localhost`. Если порт 80 занят, задай в `.env` другой, например `HTTP_PORT=8090`.

## Запуск

Если `.env` ещё нет, скопируй `.env.example` в `.env` и замени все `change_me` на свои значения. Compose проверяет наличие обязательных переменных, но принимает `change_me` как обычное значение. Команды ниже выполняются из корня репозитория.

```bash
docker compose --env-file .env -f infra/compose.yaml up -d --build
docker compose --env-file .env -f infra/compose.yaml ps
```

Чтобы остановить стенд, выполни `docker compose --env-file .env -f infra/compose.yaml down`. Флаг `-v` удаляет тома `core-db-data` и `core-backups`.

Моки на localhost (для отладки и сквозных тестов с хоста) подключаются вторым файлом:

```
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.mocks-ports.yaml up -d
```

Порты: CRM `8081`, биллинг `8082`, справочник `8083`, только на `127.0.0.1`.

Для отладки в обход Nginx можно открыть шлюз на `127.0.0.1:8000`.

```bash
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.app-ports.yaml up -d
```

### Стенд DEMO

```bash
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.demo.yaml up -d --build --wait
```

Профиль DEMO переключает нормативы SLA на минуты, загружает демо-пользователей и компании через Flyway и повышает лимиты Nginx для сида и тестов. Первая сборка может занять время, пока Maven и npm скачивают зависимости. Вход на стенд через Nginx по `http://localhost` или по порту `HTTP_PORT` из `.env`.

- [Демо-пользователи и компании](seed/README.md)
- [Создание демо-заявки через API](seed/demo-requests/README.md)
- [Запуск сквозных тестов](../tests/e2e/README.md#сквозные-тесты-api)

## Nginx

Конфигурация лежит в `ui/nginx/default.conf.template`. Что она делает:

- раздаёт собранный интерфейс, неизвестные адреса отдаёт как `index.html` (маршруты React);
- проксирует `/api/` и `/ws` в шлюз; при остановленном шлюзе отвечает `502` и показывает страницу `50x.html`;
- ограничивает тело запроса (1 МБ, для вложений 11 МБ) и частоту запросов с одного адреса: 100 в минуту на `/api`, 10 в минуту на вход, при превышении `429`;
- пишет по одной строке JSON на запрос в stdout, в ней есть `correlationId`.

Лимиты задают переменные `RATE_API` и `RATE_LOGIN`. В профиле DEMO они подняты до 6000 и 600, иначе сквозные тесты и сид упираются в лимит с одного адреса. Логи: `docker compose --env-file .env -f infra/compose.yaml logs nginx`, по `correlationId` запрос можно найти и в логах шлюза и ядра.

Версия Nginx закреплена на ветке `1.30` образа `nginxinc/nginx-unprivileged`, где выходят исправления безопасности. Обновить образ: `docker compose --env-file .env -f infra/compose.yaml build --pull nginx`. HTTP/2 и TLS на стенде не включены.

## База данных

При первом запуске `postgres/init/01-roles.sh` создаёт три роли. `CORE_DB_MIGRATION_USER` владеет схемой `public` и запускает миграции Flyway. `CORE_DB_APP_USER` работает с данными приложения. `CORE_DB_BACKUP_USER` читает данные для дампа. Скрипт срабатывает только при инициализации пустого тома. Для существующей базы меняй роли и пароли в PostgreSQL и синхронизируй значения в `.env`.

Для Кирилла: ограничение `audit_log` (приложению только `INSERT` и `SELECT`) делает миграция при создании таблицы, например `REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM core_app`. Имя роли можно брать из плейсхолдера Flyway.

Адреса моков для ядра внутри сети compose: `http://mock-crm:8080`, `http://mock-billing:8080`, `http://mock-registry:8080` (`MOCK_CRM_URL`, `MOCK_BILLING_URL`, `MOCK_REGISTRY_URL`).

## Резервное копирование

Из корня репозитория можно создать дамп вручную и посмотреть список копий.

```bash
docker compose --env-file .env -f infra/compose.yaml exec backup /usr/local/bin/backup.sh
docker compose --env-file .env -f infra/compose.yaml exec backup ls -1t /backups
```

Расписание, проверка восстановления в отдельную БД и ограничения описаны в [инструкции по бэкапу](backup/README.md).
