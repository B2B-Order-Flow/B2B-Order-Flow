# Бэкап

Требования: [nfr.md](../../docs/system/nfr.md), раздел 6. Сервис `backup` описан в [compose.yaml](../compose.yaml).

## Что и как

Контейнер `backup` (образ `postgres:16-alpine`, внутри `crond`) раз в сутки делает `pg_dump -Fc` базы `core_db` и кладёт файл `core_db_ГГГГММДД_ЧЧММСС.dump` в именованный том `core-backups` (каталог `/backups` в контейнере). Хранятся последние 7 копий, остальные удаляются сразу после нового дампа. Недописанный дамп копией не считается: файл сначала пишется как `.tmp` и переименовывается в конце.

Контейнер подключается под ролью `backup_ro`: она только читает данные (`pg_read_all_data`), писать не может. Роль создаёт `infra/postgres/init/01-roles.sh`. Контейнер сидит в отдельной внутренней сети `backup`, где кроме него только `core-db`, и в сеть остальных сервисов не входит.

| Параметр | По умолчанию | Где менять |
|---|---|---|
| Расписание cron (UTC) | `0 3 * * *`, то есть 03:00 UTC | `BACKUP_CRON` в `.env` |
| Сколько копий хранить | 7 | `BACKUP_KEEP` в `.env` |

Роль и пароль берутся из `CORE_DB_BACKUP_USER` и `CORE_DB_BACKUP_PASSWORD`.

## Вручную

Перед защитой копию нужно сделать до и после сида:

```bash
docker compose exec backup /usr/local/bin/backup.sh
docker compose exec backup ls -1t /backups
```

Достать файл из тома (каталог `infra/backup/*` в `.gitignore`, в репозиторий дампы не попадут):

```bash
docker compose cp backup:/backups/<файл>.dump infra/backup/
```

## Проверка восстановления

Копию проверяют восстановлением в отдельную базу, рабочая `core_db` не трогается. Дамп идёт через pipe между контейнерами: у `backup` нет прав создавать базы, а у `core-db` нет тома с дампами.

```bash
f=$(docker compose exec -T backup sh -c 'ls -1t /backups | head -1')
docker compose exec -T core-db psql -U postgres -c "create database restore_check"
time (docker compose exec -T backup cat /backups/$f | docker compose exec -T core-db pg_restore -U postgres -d restore_check --no-owner)
for db in core_db restore_check; do echo "== $db"; for t in app_user company audit_log; do docker compose exec -T core-db psql -U postgres -d $db -Atc "select '$t', count(*) from $t"; done; done
docker compose exec -T core-db psql -U postgres -c "drop database restore_check"
```

Результат проверки, 20.11.2026, стенд DEMO:

| Параметр | Значение |
|---|---|
| Дамп | `core_db_20261120_202418.dump`, сделан вручную после применения сида |
| Восстановление в `restore_check` | без ошибок, 1,0 секунды |
| Строк в `core_db` и `restore_check` | `app_user` 23 и 23, `company` 10 и 10, `audit_log` 4 и 4 |
| Цель RTO из nfr | 30 минут, фактически восстановление занимает секунды (на объёме демо-данных) |

## Что не покрыто

- Вложения: тома `core-files` в compose пока нет, поэтому `tar` вложений не делается. Добавим вместе с томом.
- Замена рабочей базы копией (остановить ядро, пересоздать `core_db`, восстановить) не отрабатывалась, проверено только восстановление в отдельную базу.
- Копии лежат в том же Docker-хосте, что и база. Если потерять хост целиком, пропадут и они: для прототипа это принято, внешнего хранилища нет.
