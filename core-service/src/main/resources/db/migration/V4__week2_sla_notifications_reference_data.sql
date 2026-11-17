-- Week 2 reference data and SLA/notification infrastructure.
-- The DEMO profile uses wall-clock minutes; PROD uses the work calendar below.

alter table request
    add column tech_reject_reason_code text;

create table routing_config (
    config_key  text primary key,
    config_value jsonb not null,
    description text,
    updated_at  timestamptz not null default now()
);

create table cancel_reason (
    code             text primary key,
    title            text not null,
    party            text not null,
    requires_comment boolean not null default false,
    active           boolean not null default true,
    constraint ck_cancel_reason_party check (party in ('CLIENT', 'OPERATOR'))
);

create table rejection_reason (
    code       text primary key,
    title      text not null,
    stage_type text,
    active     boolean not null default true
);

alter table request
    add constraint fk_request_tech_reject_reason
        foreign key (tech_reject_reason_code) references rejection_reason (code) on delete restrict;

create table sla_norm (
    id              uuid primary key default gen_random_uuid(),
    profile         text not null,
    kind            text not null,
    stage_type      text,
    norm_minutes    integer not null,
    warn_before_minutes integer not null,
    calendar_kind   text not null,
    created_at      timestamptz not null default now(),
    constraint ck_sla_norm_profile check (profile in ('PROD', 'DEMO')),
    constraint ck_sla_norm_kind check (kind in (
        'STAGE_SLA', 'DATA_CHECK', 'MANAGER_ASSIGNMENT', 'TECH_RESULT',
        'ACCEPTANCE', 'CANCEL_RESPONSE'
    )),
    constraint ck_sla_norm_stage_type check (
        (kind = 'STAGE_SLA' and stage_type is not null) or
        (kind <> 'STAGE_SLA' and stage_type is null)
    ),
    constraint ck_sla_norm_minutes check (norm_minutes > 0),
    constraint ck_sla_norm_warn check (warn_before_minutes >= 0 and warn_before_minutes < norm_minutes),
    constraint ck_sla_norm_calendar check (calendar_kind in ('WORK', 'CALENDAR')),
    constraint ux_sla_norm_profile_kind_stage unique (profile, kind, stage_type)
);

create table pause_reason (
    code            text primary key,
    title           text not null,
    automatic       boolean not null default false,
    setter_roles    text[] not null default '{}',
    remover_roles   text[] not null default '{}',
    active          boolean not null default true
);

create table work_calendar (
    calendar_date   date primary key,
    is_working      boolean not null,
    note            text,
    timezone        text not null default 'Europe/Moscow'
);

create table sla_timer (
    id                    uuid primary key default gen_random_uuid(),
    request_id            uuid not null references request (id) on delete restrict,
    stage_id              uuid references request_stage (id) on delete restrict,
    kind                  text not null,
    norm_minutes          integer not null,
    status                text not null default 'RUNNING',
    started_at            timestamptz not null,
    warn_at               timestamptz,
    due_at                timestamptz,
    escalate_at           timestamptz,
    elapsed_sla_seconds   bigint not null default 0,
    next_event_at         timestamptz,
    warned_at             timestamptz,
    breached_at           timestamptz,
    escalation_level      smallint not null default 0,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint ck_sla_timer_kind check (kind in (
        'STAGE_SLA', 'DATA_CHECK', 'MANAGER_ASSIGNMENT', 'TECH_RESULT',
        'ACCEPTANCE', 'CANCEL_RESPONSE'
    )),
    constraint ck_sla_timer_stage check ((kind = 'STAGE_SLA') = (stage_id is not null)),
    constraint ck_sla_timer_status check (status in ('RUNNING', 'PAUSED', 'STOPPED')),
    constraint ck_sla_timer_norm check (norm_minutes > 0),
    constraint ck_sla_timer_escalation check (escalation_level between 0 and 2)
);

create index ix_sla_timer_next_event
    on sla_timer (next_event_at)
    where status in ('RUNNING', 'PAUSED');
create index ix_sla_timer_request on sla_timer (request_id);
create index ix_sla_timer_stage on sla_timer (stage_id);

create table sla_pause (
    id              uuid primary key default gen_random_uuid(),
    timer_id        uuid not null references sla_timer (id) on delete restrict,
    reason_code     text not null references pause_reason (code) on delete restrict,
    set_by          text not null,
    comment         text not null,
    started_at      timestamptz not null,
    ended_at        timestamptz,
    limit_at        timestamptz not null,
    extended_count  integer not null default 0,
    extended_by     uuid references app_user (id) on delete restrict,
    created_at      timestamptz not null default now(),
    constraint ck_sla_pause_set_by check (set_by = 'SYSTEM' or set_by ~ '^[0-9a-fA-F-]{36}$'),
    constraint ck_sla_pause_comment check (char_length(comment) between 1 and 4000),
    constraint ck_sla_pause_extended_count check (extended_count >= 0),
    constraint ck_sla_pause_dates check (ended_at is null or ended_at >= started_at)
);

create index ix_sla_pause_timer on sla_pause (timer_id, started_at);

create table notification (
    id                  uuid primary key default gen_random_uuid(),
    recipient_user_id   uuid not null references app_user (id) on delete restrict,
    request_id          uuid references request (id) on delete restrict,
    event_type          text not null,
    payload             jsonb not null default '{}',
    created_at          timestamptz not null default now(),
    read_at             timestamptz,
    published_at        timestamptz,
    attempts            integer not null default 0,
    constraint ck_notification_attempts check (attempts >= 0)
);

create index ix_notification_recipient_unread
    on notification (recipient_user_id, created_at)
    where read_at is null;
create index ix_notification_published on notification (published_at, created_at);

create table ws_ticket (
    id              uuid primary key default gen_random_uuid(),
    ticket_hash     text not null unique,
    user_id         uuid not null references app_user (id) on delete restrict,
    active_role     text not null,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    created_at      timestamptz not null default now()
);

create index ix_ws_ticket_expiry on ws_ticket (expires_at);

insert into routing_config (config_key, config_value, description) values
    ('standard_speed_max_mbps', '100', 'Maximum standard connection speed'),
    ('debt_threshold_rub', '0', 'Debt threshold for financial approval'),
    ('capex_mgmt_threshold_rub', '500000', 'CAPEX threshold for management approval'),
    ('strategic_segments', '["STRATEGIC"]', 'Segments requiring management approval');

insert into pause_reason (code, title, automatic, setter_roles, remover_roles) values
    ('REWORK', 'Возврат на доработку', true, '{SYSTEM}', '{SYSTEM}'),
    ('AWAIT_CLIENT_SIGNATURE', 'Ожидание подписи клиента', true, '{SYSTEM}', '{SYSTEM}'),
    ('AWAIT_CLIENT_DATA', 'Ожидание данных или документов клиента', false, '{COORDINATOR}', '{COORDINATOR}'),
    ('NO_ACCESS', 'Нет доступа на объект', false, '{COORDINATOR}', '{COORDINATOR}'),
    ('CLIENT_RESCHEDULED', 'Клиент перенёс дату работ', false, '{COORDINATOR}', '{COORDINATOR}'),
    ('CANCEL_REVIEW', 'Рассмотрение запроса на отмену', true, '{SYSTEM}', '{SYSTEM}'),
    ('RECHECK_TECH', 'Повторная техническая проверка', true, '{SYSTEM}', '{SYSTEM}');

insert into rejection_reason (code, title, stage_type, active)
values ('OTHER', 'Другая причина', null, true)
on conflict (code) do nothing;

insert into cancel_reason (code, title, party, requires_comment) values
    ('CLIENT_CHANGED_MIND', 'Клиент передумал', 'CLIENT', false),
    ('CLIENT_CHOSE_OTHER_PROVIDER', 'Клиент выбрал другого провайдера', 'CLIENT', false),
    ('CLIENT_PLANS_CHANGED', 'Изменились планы клиента', 'CLIENT', false),
    ('CLIENT_TERMS_UNACCEPTABLE', 'Не устраивают условия или сроки', 'CLIENT', false),
    ('CLIENT_OTHER', 'Другое', 'CLIENT', true),
    ('DUPLICATE', 'Дубль заявки', 'OPERATOR', false),
    ('INPUT_ERROR', 'Ошибка ввода', 'OPERATOR', false),
    ('CLIENT_UNAVAILABLE', 'Клиент не выходит на связь', 'OPERATOR', false),
    ('CONNECTION_IMPOSSIBLE', 'Подключение невозможно', 'OPERATOR', false),
    ('OPERATOR_OTHER', 'Другое', 'OPERATOR', true)
on conflict (code) do nothing;

insert into sla_norm (profile, kind, stage_type, norm_minutes, warn_before_minutes, calendar_kind) values
    ('PROD', 'STAGE_SLA', 'TECH_CHECK', 1440, 360, 'WORK'),
    ('PROD', 'STAGE_SLA', 'RECHECK_TECH', 960, 240, 'WORK'),
    ('PROD', 'STAGE_SLA', 'LEGAL_APPROVAL', 1440, 360, 'WORK'),
    ('PROD', 'STAGE_SLA', 'FINANCE_APPROVAL', 960, 240, 'WORK'),
    ('PROD', 'STAGE_SLA', 'PROCUREMENT_APPROVAL', 1440, 360, 'WORK'),
    ('PROD', 'STAGE_SLA', 'MGMT_APPROVAL', 960, 240, 'WORK'),
    ('PROD', 'STAGE_SLA', 'CONTRACT_SIGNING', 960, 240, 'WORK'),
    ('PROD', 'STAGE_SLA', 'CONSTRUCTION', 43200, 10800, 'CALENDAR'),
    ('PROD', 'STAGE_SLA', 'INSTALLATION', 2400, 600, 'WORK'),
    ('PROD', 'STAGE_SLA', 'ACTIVATION', 480, 120, 'WORK'),
    ('PROD', 'STAGE_SLA', 'REMEDIATION', 1440, 360, 'WORK'),
    ('PROD', 'STAGE_SLA', 'CLOSING', 480, 120, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'TECH_CHECK', 5, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'RECHECK_TECH', 4, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'LEGAL_APPROVAL', 4, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'FINANCE_APPROVAL', 3, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'PROCUREMENT_APPROVAL', 4, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'MGMT_APPROVAL', 3, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'CONTRACT_SIGNING', 3, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'CONSTRUCTION', 5, 1, 'CALENDAR'),
    ('DEMO', 'STAGE_SLA', 'INSTALLATION', 5, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'ACTIVATION', 2, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'REMEDIATION', 4, 1, 'WORK'),
    ('DEMO', 'STAGE_SLA', 'CLOSING', 2, 1, 'WORK'),
    ('PROD', 'DATA_CHECK', null, 240, 60, 'WORK'),
    ('PROD', 'MANAGER_ASSIGNMENT', null, 240, 60, 'WORK'),
    ('PROD', 'TECH_RESULT', null, 43200, 7200, 'CALENDAR'),
    ('PROD', 'ACCEPTANCE', null, 2400, 960, 'WORK'),
    ('PROD', 'CANCEL_RESPONSE', null, 4800, 960, 'WORK'),
    ('DEMO', 'DATA_CHECK', null, 3, 1, 'WORK'),
    ('DEMO', 'MANAGER_ASSIGNMENT', null, 2, 1, 'WORK'),
    ('DEMO', 'TECH_RESULT', null, 12, 2, 'CALENDAR'),
    ('DEMO', 'ACCEPTANCE', null, 3, 1, 'WORK'),
    ('DEMO', 'CANCEL_RESPONSE', null, 4, 1, 'WORK');

insert into work_calendar (calendar_date, is_working, note)
select day::date,
       extract(isodow from day) between 1 and 5
           and day::date not in ('2026-11-04', '2026-12-31', '2027-01-01', '2027-01-02',
                                 '2027-01-03', '2027-01-04', '2027-01-05', '2027-01-06',
                                 '2027-01-07', '2027-01-08') ,
       case
           when day::date in ('2026-11-04') then 'День народного единства'
           when day::date between '2026-12-31' and '2027-01-08' then 'Новогодние каникулы'
           when extract(isodow from day) > 5 then 'Выходной'
           else 'Рабочий день'
       end
from generate_series('2026-11-01'::date, '2027-01-31'::date, interval '1 day') as days(day);
