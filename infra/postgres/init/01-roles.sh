#!/bin/sh
# Выполняется один раз, при первом создании тома core-db-data.
# Суперпользователь postgres нужен только здесь. Дальше схему создаёт и меняет
# CORE_DB_MIGRATION_USER (Flyway), а ядро работает под CORE_DB_APP_USER.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v migrator="$CORE_DB_MIGRATION_USER" -v migrator_pw="$CORE_DB_MIGRATION_PASSWORD" \
  -v app="$CORE_DB_APP_USER" -v app_pw="$CORE_DB_APP_PASSWORD" <<'SQL'
CREATE ROLE :"migrator" LOGIN PASSWORD :'migrator_pw';
CREATE ROLE :"app" LOGIN PASSWORD :'app_pw';

REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
-- Мигратору ещё CREATE: Flyway и миграции могут создавать схемы и расширения
GRANT CONNECT, CREATE ON DATABASE :"db" TO :"migrator";

-- Схему public ведёт мигратор, приложение только пользуется объектами
ALTER SCHEMA public OWNER TO :"migrator";
GRANT USAGE ON SCHEMA public TO :"app";

-- Всё, что мигратор создаёт в public, приложение получает с правами на данные.
-- Ограничение audit_log (только INSERT и SELECT) делает миграция Flyway при создании таблицы.
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator" IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
