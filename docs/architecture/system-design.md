
# Системный проект и компонентная архитектура (System Design)

Настоящий документ описывает компонентное устройство, внутреннее разделение ответственности, динамику взаимодействия модулей и инженерные решения по надежности, безопасности и хранению данных системы **B2B Order Flow**.

---

## 1. Верхнеуровневый системный контекст (C4 Context)

Система **B2B Order Flow** выступает связующим звеном между корпоративными клиентами, операционным контуром МТС и внешними справочно-учетными сервисами.

```mermaid
graph TD
    %% Акторы
    Client[Корпоративный Клиент]
    Op[Оператор / Менеджер МТС]
    Legal[Согласующий: Юрист / Финансист]

    %% Наша система
    subgraph B2B_Order_Flow [Контур B2B Order Flow]
        BFF[API Gateway / BFF - FastAPI]
        CORE[Транзакционное Ядро - Spring Boot]
    end

    %% Внешние сервисы (Mocks)
    subgraph External_Mocks [Экосистема МТС / Mock-сервисы]
        CRM[Mock CRM - Клиенты, Сегменты]
        Billing[Mock Биллинг - Баланс, Задолженность]
        Ref[Mock Справочник - ИНН / Адреса]
    end

    %% Связи
    Client -->|HTTPS / WebSockets| BFF
    Op -->|HTTPS / WebSockets| BFF
    Legal -->|HTTPS / WebSockets| BFF

    BFF -->|Internal REST API| CORE
    CORE -->|REST + Spring Retry| CRM
    CORE -->|REST + Spring Retry| Billing
    CORE -->|REST + Spring Retry| Ref

```

---

## 2. Детализация компонентной архитектуры (Containers)

Система построена на принципах **гибридной микросервисной архитектуры** с разграничением слоев ответственности (BFF vs Core) и соблюдением паттерна **Database-per-Service**.

```mermaid
graph LR
    subgraph Client_Layer [Front Layer]
        UI[React UI / Web-панель]
    end

    subgraph Gateway_Layer [BFF / Gateway Contour]
        FastAPI[FastAPI Gateway]
        DB_BFF[(fastapi_db\nPostgreSQL)]
        FastAPI <--> DB_BFF
    end

    subgraph Core_Layer [Domain Core Contour]
        SpringBoot[Spring Boot 3.x Core]
        DB_Core[(core_db\nPostgreSQL 16)]
        FS[Secure Storage / Files]
        
        SpringBoot <--> DB_Core
        SpringBoot <--> FS
    end

    subgraph Mock_Layer [Mocks]
        WireMock[WireMock Service]
    end

    UI -->|1. External REST API| FastAPI
    UI <-->|2. WebSockets Push| FastAPI
    FastAPI -->|3. Internal REST API| SpringBoot
    SpringBoot -->|4. HTTP / REST| WireMock

```

---

## 3. Декомпозиция модулей транзакционного ядра (Core System Breakdown)

Для обеспечения чистой архитектуры и изоляции контекстов Java-ядро разбито на 8 специализированных функциональных модулей:

```mermaid
graph TD
    subgraph Spring_Boot_Core [Spring Boot Core Engine]
        RE[1. Request Engine - Создание и версионирование заявок]
        FSM[2. FSM Router - Статусы и динамическая маршрутизация]
        SLA[3. SLA Monitor - Таймеры и управление эскалацией]
        AP[4. Approval Engine - Подсистема параллельных согласователей]
        DOC[5. Document & File Engine - Печатные формы и вложения]
        AUD[6. Audit & History - Неизменяемый журнал аудита]
        INT[7. Integration Gateway - Взаимодействие с моками и Retry]
        NOTIF[8. Notification Publisher - Шина внутренних событий]
    end

```

### Назначение модулей:

1. **Request Engine:** Отвечает за CRUD-операции над заявками, валидацию параметров ШПД, связь с контрагентами и поддержку дочерних заявок.
2. **FSM Router:** Реализует конечный автомат статусов (5 фаз O2A), проверяет доступ ролей к переходам и вычисляет ветку процесса (наличие стройки, необходимость согласований).
3. **SLA Monitor:** Циклический фоновый процесс (`@Scheduled`), рассчитывающий нормы времени на этапы, учитывающий допустимые паузы и генерирующий предупреждения при 25% остатка времени.
4. **Approval Engine:** Формирует задачи для профильных отделов (юристы, финансы, закупки, спец-согласования) и агрегирует их решения.
5. **Document & File Engine:** Отвечает за загрузку и бинарное хранение актов/договоров, а также подстановку данных заявки в печатные шаблоны.
6. **Audit & History:** Гарантирует нестираемую фиксацию каждого действия (`AuditLog`) с указанием автора, timestamp и старого/нового значения.
7. **Integration Gateway:** Изолирует вызовы внешних REST API (CRM, Биллинг) и обеспечивающий отказоустойчивость при помощи **Spring Retry**.
8. **Notification Publisher:** Публикует `Spring Events` для трансляции событий на FastAPI BFF и последующего push-уведомления в UI.

---

## 4. Динамика взаимодействия: Сквозной Sequence-поток (SC-01 / SC-03)

Ниже представлена диаграмма последовательности вызовов при создании заявки и автоматической обработке статуса:

```mermaid
sequenceDiagram
    autonumber
    actor U as Пользователь (UI)
    participant B as FastAPI (BFF)
    participant C as Spring Boot (Core)
    participant M as Mock CRM / Billing
    participant DB as core_db (PostgreSQL)

    U->>B: POST /api/v1/requests (JSON)
    B->>B: Валидация схемы (Pydantic v2)
    B->>C: POST /internal/v1/requests (Internal REST)
    
    rect rgb(240, 248, 255)
        note over C,DB: ACID Транзакция ядра (@Transactional)
        C->>M: GET /crm/client-info (Проверка сегмента)
        M-->>C: 200 OK (Данные клиента)
        C->>C: FSM: Вычисление первичного маршрута
        C->>DB: INSERT INTO requests & request_stages
        C->>DB: INSERT INTO audit_logs (Запись истории)
    end

    C-->>B: 201 Created (ID заявки, Статус: NEW)
    B-->>U: 201 Created (Response DTO)

    opt Генерация уведомления
        C->>B: POST /internal/v1/notifications/push
        B-->>U: WebSocket Event: "Заявка #X успешно создана"
    end

```

---

### 5. Конфиденциальность данных и локализация

* **Категории данных:** Персональные данные (ФИО, контакты), финансовые параметры (коммерческие скидки, индивидуальные тарифы, CAPEX на стройку), юридические реквизиты (ИНН/ОГРН, тексты договоров) и таблицы аудита (`AuditLog`).
* **Локализация:** Все конфиденциальные данные хранятся **строго в `core_db` (БД транзакционного ядра)**.
* **Безопасность:** БД ядра находится в закрытом сетевом контуре Docker (Private VPC/Network) без внешних портов наружу. Доступ к ней имеет только контейнер Spring Boot. Авторизация на уровне API выполняется по стандартам JWT + Spring Security (RBAC).

---

### 6. Локализация SLA, истории и уведомлений

* **SLA-контроль:** Находится в Java-ядре. Фоновый сервис (`@Scheduled` + `Clock`) циклически запрашивает активные этапы, рассчитывает остаток времени и генерирует события эскалации при остатке $\le 25\%$.
* **История изменений:** Таблица `audit_logs` в `core_db`. Запись производится атомарно в той же ACID-транзакции, что и изменение самой заявки.
* **Уведомления:** Генерируются ядром и передаются на FastAPI BFF, удерживающий активные WebSocket-сессии с клинетами и операторами.

---

### 7. Обеспечение согласованности данных при сбоях (ACID & Failover)

* **Транзакционность:** Аннотация `@Transactional` в Java-ядре гарантирует откат всей операции при любых ошибках (откат смены статуса, создания задачи и записи аудита).
* **Сбои внешних систем (Mock-сервисы):** Интеграционный слой ядра обернут в паттерн **Spring Retry** (экспоненциальная задержка при повторных попытках). При недоступности мока заявка переходит во временный статус ожидания интеграции с фиксированием ошибки, исключая повреждение данных.

---

## 8. Сводный технологический стек по компонентам

| Компонент | Технологии / Библиотеки | Назначение |
| --- | --- | --- |
| **API Gateway / BFF** | Python 3.11, FastAPI, Pydantic v2, Uvicorn, `httpx` | Единая точка входа, валидация DTO, проксирование |
| **Real-time Push** | WebSockets (FastAPI) | Мгновенная доставка уведомлений в UI |
| **Gateway DB** | PostgreSQL 16 (`fastapi_db`) | Кэш сессий, временные контексты, WS-клиенты |
| **Domain Core** | Java 21, Spring Boot 3.x, Spring Data JPA, Spring Security | Бизнес-логика, FSM, SLA, транзакции, аудит |
| **Core DB & Migrations** | PostgreSQL 16 (`core_db`), Flyway | Хранение сущностей домена, миграции схем |
| **Интеграции & Retry** | Spring WebClient / RestTemplate, Spring Retry | Безопасный вызов внешних сервисов |
| **Тестирование & Моки** | JUnit 5, Testcontainers, WireMock, Playwright | Unit, интеграционные и E2E тесты |
| **Инфраструктура** | Docker, Docker Compose, GitHub Actions | Контейнеризация и автоматизация CI/CD |
