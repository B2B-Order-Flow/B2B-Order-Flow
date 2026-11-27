-- Week 3: approval decisions, cancellation workflow and rejection references.
-- Source of truth: docs/system/domain-model.md, section 4, and
-- docs/business/business-process.md, sections 10.2 and 11.6.

create table approval_decision (
    id                  uuid primary key default gen_random_uuid(),
    stage_id            uuid not null references request_stage (id) on delete restrict,
    decided_by          uuid not null references app_user (id) on delete restrict,
    decision            text not null,
    comment             text,
    reject_reason_code  text references rejection_reason (code) on delete restrict,
    scope_hash          text not null,
    scope_snapshot      jsonb not null default '{}',
    decided_at          timestamptz not null default now(),
    superseded_at       timestamptz,
    created_at          timestamptz not null default now(),
    constraint ck_approval_decision_value
        check (decision in ('APPROVE', 'RETURN', 'REJECT')),
    constraint ck_approval_decision_comment
        check (decision = 'APPROVE' or comment is not null and char_length(comment) between 1 and 4000),
    constraint ck_approval_decision_reject_reason
        check ((decision = 'REJECT' and reject_reason_code is not null)
            or (decision <> 'REJECT' and reject_reason_code is null)),
    constraint ck_approval_decision_scope_hash
        check (char_length(scope_hash) > 0)
);

create index ix_approval_decision_stage on approval_decision (stage_id, decided_at);
create index ix_approval_decision_active on approval_decision (stage_id)
    where superseded_at is null;

create table cancellation_request (
    id                  uuid primary key default gen_random_uuid(),
    request_id          uuid not null references request (id) on delete restrict,
    version             bigint not null default 0,
    initiator           text not null,
    method              text not null,
    level_at_request    smallint not null,
    status              text not null default 'OPEN',
    reason_code         text not null references cancel_reason (code) on delete restrict,
    reason_comment      text,
    previous_status     text,
    cost_amount         numeric(14, 2),
    cost_breakdown      jsonb not null default '[]',
    cost_fixed_by       uuid references app_user (id) on delete restrict,
    cost_confirmed_by   uuid references app_user (id) on delete restrict,
    approved_by         uuid references app_user (id) on delete restrict,
    requested_by        uuid not null references app_user (id) on delete restrict,
    requested_at        timestamptz not null default now(),
    resolved_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint ck_cancellation_request_initiator
        check (initiator in ('CLIENT', 'OPERATOR')),
    constraint ck_cancellation_request_method
        check (method in ('UNILATERAL', 'AGREEMENT')),
    constraint ck_cancellation_request_level
        check (level_at_request between 0 and 2),
    constraint ck_cancellation_request_status
        check (status in ('OPEN', 'ACCEPTED', 'WITHDRAWN', 'REJECTED', 'EXPIRED')),
    constraint ck_cancellation_request_reason_comment
        check (reason_comment is null or char_length(reason_comment) between 1 and 4000),
    constraint ck_cancellation_request_cost
        check (cost_amount is null or cost_amount >= 0)
);

create unique index ux_cancellation_request_open
    on cancellation_request (request_id)
    where status = 'OPEN';
create index ix_cancellation_request_request
    on cancellation_request (request_id, requested_at);
create index ix_cancellation_request_status
    on cancellation_request (status, requested_at);

-- Week 3 rejection catalog. OTHER remains the single cross-stage fallback.
insert into rejection_reason (code, title, stage_type, active) values
    ('TECH_NO_NETWORK', 'Нет сети оператора в районе подключения', 'TECH_CHECK', true),
    ('TECH_CONSTRUCTION_IMPOSSIBLE', 'Невозможно проложить линию до объекта', 'TECH_CHECK', true),
    ('TECH_NO_CAPACITY', 'Нет свободной ёмкости сети по адресу', 'TECH_CHECK', true),
    ('TECH_SPEED_UNAVAILABLE', 'Запрошенная скорость по адресу недоступна', 'TECH_CHECK', true),
    ('TECH_NO_BUILDING_PERMISSION', 'Нет разрешения на размещение оборудования', 'TECH_CHECK', true),
    ('LEGAL_TERMS_UNACCEPTABLE', 'Условия договора неприемлемы для оператора', 'LEGAL_APPROVAL', true),
    ('LEGAL_GOV_REQUIREMENTS', 'Невозможно выполнить требования госзаказчика', 'LEGAL_APPROVAL', true),
    ('LEGAL_CLIENT_STATUS', 'Правовой статус организации не позволяет заключить договор', 'LEGAL_APPROVAL', true),
    ('FIN_DEBT', 'Есть непогашенная задолженность', 'FINANCE_APPROVAL', true),
    ('FIN_PRICE_UNACCEPTABLE', 'Цена или скидка вне допустимых условий', 'FINANCE_APPROVAL', true),
    ('FIN_LOW_PAYBACK', 'Подключение экономически нецелесообразно', 'FINANCE_APPROVAL', true),
    ('FIN_CLIENT_RISK', 'Не пройдена финансовая проверка клиента', 'FINANCE_APPROVAL', true),
    ('PROC_NO_CONTRACTOR', 'Нет подрядчика для выполнения работ', 'PROCUREMENT_APPROVAL', true),
    ('PROC_NO_EQUIPMENT', 'Нет необходимого оборудования', 'PROCUREMENT_APPROVAL', true),
    ('PROC_COST_EXCEEDED', 'Стоимость работ выше допустимой', 'PROCUREMENT_APPROVAL', true),
    ('MGMT_LOW_PAYBACK', 'Проект не окупается в допустимый срок', 'MGMT_APPROVAL', true),
    ('MGMT_NO_BUDGET', 'Нет бюджета на реализацию проекта', 'MGMT_APPROVAL', true),
    ('MGMT_NOT_PRIORITY', 'Проект не входит в приоритеты развития сети', 'MGMT_APPROVAL', true)
on conflict (code) do update
set title = excluded.title,
    stage_type = excluded.stage_type,
    active = excluded.active;
