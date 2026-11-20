#!/bin/sh
# Один дамп core_db в том /backups, затем чистка: остаются последние BACKUP_KEEP копий.
# Подключение берётся из PGHOST, PGDATABASE, PGUSER, PGPASSWORD.
set -eu

dir=/backups
name="core_db_$(date -u +%Y%m%d_%H%M%S).dump"
trap 'rm -f "$dir/$name.tmp"' EXIT

pg_dump -Fc -f "$dir/$name.tmp"
mv "$dir/$name.tmp" "$dir/$name"

ls -1t "$dir"/core_db_*.dump | tail -n +$(( ${BACKUP_KEEP:-7} + 1 )) | xargs -r rm -f
echo "backup ok: $name"
