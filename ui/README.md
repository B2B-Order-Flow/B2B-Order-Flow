# ui

Веб-интерфейс B2B-Order-Flow: панель оператора и клиентский контур в одном приложении. Требования к экранам — [docs/business/ui-requirements.md](../docs/business/ui-requirements.md).

Владелец: Егор.

## Стек

React 19, Vite, TypeScript, TanStack Query, React Router, Material UI. Тесты — Vitest и React Testing Library.

## Запуск

Нужен Node.js 22 или новее.

```bash
cd ui
npm ci
npm run dev
```

Интерфейс открывается на `http://localhost:5173`. Запросы `/api` и `/ws` Vite проксирует в шлюз (BFF), по умолчанию `http://localhost:8000`. Другой адрес шлюза задаётся переменной `UI_API_TARGET`, например в `ui/.env.local` (файл не коммитится):

```bash
UI_API_TARGET=http://localhost:8080
```

## Команды

| Команда                           | Что делает                         |
| --------------------------------- | ---------------------------------- |
| `npm run dev`                     | Сервер разработки с прокси на шлюз |
| `npm run build`                   | Проверка типов и сборка в `dist/`  |
| `npm run typecheck`               | Только проверка типов              |
| `npm test`                        | Unit-тесты (Vitest, jsdom)         |
| `npm run format` / `format:check` | Prettier: исправить / проверить    |

## Структура

```text
src/
├── api/          — слой запросов: клиент, ошибки, операции, типы контракта
├── session/      — сессия (токен в sessionStorage), роли, меню, доступ к экранам
├── routes/       — маршруты и охрана (вход, доступ по роли)
├── components/   — каркас (шапка, меню, переключатель ролей) и общие состояния
├── pages/        — экраны
├── texts/ru.ts   — все тексты интерфейса и подписи кодов API
└── lib/          — форматы дат, денег, скорости
```

## Договорённости

- Запросы идут только через `src/api`: он добавляет `Authorization`, `X-Active-Role`, `Idempotency-Key` и превращает ответ `problem+json` в `ApiError` с кодом. Экраны ветвятся по `error.code`, а не по тексту.
- Повторяются только чтения при сбое связи; запись не повторяется ([api-contract.md](../docs/system/api-contract.md), раздел 5).
- Любой текст для пользователя — в `src/texts/ru.ts`, не в разметке.
- Видимость экранов и пунктов меню — в `src/session/roles.ts` по матрице прав; итоговую проверку прав делает ядро.
- На элементах, по которым ходят сквозные тесты, — стабильные `data-testid` (ui-requirements.md, раздел 11.3).
