#!/bin/sh
# Пишет расписание и запускает cron на переднем плане. Вывод заданий уходит в docker logs.
set -eu

echo "${BACKUP_CRON:-0 3 * * *} /usr/local/bin/backup.sh >> /proc/1/fd/1 2>&1" > /etc/crontabs/root
exec crond -f -l 8
