# bff-gateway

Python-шлюз для интерфейса: FastAPI, Pydantic v2, WebSocket. Не хранит состояние и не имеет своей БД, бизнес-правила проверяет ядро.

Владелец: Денис.

## Запуск локально

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Проверки перед PR:

```bash
pip install -r requirements-dev.txt
ruff check .
ruff format --check .
python scripts/check_openapi_contract.py --core ../core-service/src/main/resources/openapi/core-api.yaml
```

Проверка OpenAPI запускается после появления внутреннего контракта ядра. Она завершается
ошибкой, если операция из внешнего контракта BFF отсутствует в контракте `core-service`.

Основные настройки задаются через переменные окружения: `CORE_URL`, `INTERNAL_TOKEN`,
`CORE_HTTP_TIMEOUT_SECONDS` и `CORE_HTTP_CONNECT_TIMEOUT_SECONDS`.

BFF не хранит состояние и не реализует бизнес-правила: запросы проксируются в `core-service`.
При отсутствии `X-Correlation-Id` шлюз генерирует UUID и возвращает его в ответе. Ошибки
соединения с ядром возвращаются как `application/problem+json` с кодами `CORE_UNAVAILABLE`
или `CORE_TIMEOUT` и `retryable: true`.
