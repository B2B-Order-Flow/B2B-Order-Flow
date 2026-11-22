# Демо-заявка SC-01

Скрипт создаёт черновик заявки и отправляет её через BFF. Запись напрямую в
таблицы не выполняется, поэтому ядро создаёт историю, проверки интеграций и SLA.

Перед запуском должны быть подняты стенд в DEMO-профиле и миграции `db/demo`.

```powershell
$env:BFF_URL = "http://localhost:8000"
$env:DEMO_LOGIN = "a.sokolov"
$env:DEMO_PASSWORD = "password"
$env:DEMO_COMPANY_NAME = "ИП Соколов А. В."
python infra/seed/demo-requests/seed_demo_requests.py
```

Переменные окружения имеют значения по умолчанию для локального демо. При
повторном запуске создаётся новая заявка благодаря новому `Idempotency-Key`.
