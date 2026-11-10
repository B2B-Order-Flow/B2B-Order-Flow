-- Заявки, предложения услуги, этапы, ревизии, проверки внешних систем, аудит
-- и ключи идемпотентности. Соглашения — docs/system/domain-model.md, раздел 8.

-- Человекочитаемый номер заявки: REQ-ГГГГ-NNNNNN.
create sequence request_number_seq start 1 increment 1;

create table request (
    id                  uuid        primary key default gen_random_uuid(),
    number              text        not null,
    company_id          uuid        not null references company (id) on delete restrict,
    created_by          uuid        not null references app_user (id) on delete restrict,
    parent_request_id   uuid        references request (id) on delete restrict,

    status              text        not null default 'DRAFT',
    previous_status     text,
    status_changed_at   timestamptz not null default now(),
    integration_error   boolean     not null default false,
    commitment_level    smallint    not null default 0,
    request_class       text,
    route_version       integer     not null default 0,
    version             bigint      not null default 0,

    manager_id          uuid        references app_user (id) on delete restrict,

    address             text,
    requested_speed_mbps integer,
    desired_date        date,
    contact_name        text,
    contact_phone       text,
    contract_preference text,
    client_comment      text,

    segment             text,
    is_gov_customer     boolean,
    debt_amount         numeric(14, 2),
    crm_manager_id      uuid        references app_user (id) on delete restrict,

    tech_result         text,
    tech_reject_reason  text,
    capex               numeric(14, 2),
    construction_days   integer,

    selected_offer_id   uuid,

    contract_number         text,
    contract_sent_at        timestamptz,
    contract_concluded_at   timestamptz,
    work_started_at         timestamptz,
    activated_at            timestamptz,
    act_sent_at             timestamptz,
    closed_at               timestamptz,
    accepted_by_default     boolean not null default false,

    search_vector       tsvector,
    submitted_at        timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    deleted_at          timestamptz,

    constraint ck_request_status check (status in (
        'DRAFT', 'DATA_CHECK', 'WITH_MANAGER', 'TECH_CHECK', 'IN_APPROVAL', 'REWORK',
        'CONTRACT_SIGNING', 'IN_EXECUTION', 'ACTIVATED', 'CANCEL_REQUESTED',
        'CLOSED', 'REJECTED', 'CANCELLED'
    )),
    constraint ck_request_previous_status check (
        previous_status is null or previous_status in (
            'DRAFT', 'DATA_CHECK', 'WITH_MANAGER', 'TECH_CHECK', 'IN_APPROVAL', 'REWORK',
            'CONTRACT_SIGNING', 'IN_EXECUTION', 'ACTIVATED', 'CANCEL_REQUESTED',
            'CLOSED', 'REJECTED', 'CANCELLED'
        )
    ),
    constraint ck_request_commitment_level check (commitment_level between 0 and 3),
    constraint ck_request_segment check (segment is null or segment in ('SMB', 'LARGE', 'STRATEGIC')),
    constraint ck_request_contract_preference check (
        contract_preference is null or contract_preference in ('OFFER', 'STANDARD', 'CLIENT_FORM')
    ),
    constraint ck_request_speed check (requested_speed_mbps is null or requested_speed_mbps > 0),
    constraint ck_request_number_format check (number ~ '^REQ-[0-9]{4}-[0-9]{6}$')
);

create unique index ux_request_number on request (number);
create index ix_request_company_status on request (company_id, status);
create index ix_request_manager_status on request (manager_id, status);
create index ix_request_parent on request (parent_request_id);
create index ix_request_status on request (status);
create index ix_request_search_vector on request using gin (search_vector);

-- Полнотекстовый поиск по архиву: номер, адрес и название компании.
-- Колонка обновляется триггером (docs/system/domain-model.md, 4.2).
create or replace function update_request_search_vector() returns trigger
    language plpgsql
as $$
declare
    company_name text;
begin
    select name into company_name from company where id = new.company_id;

    new.search_vector :=
        setweight(to_tsvector('russian', coalesce(new.number, '')), 'A') ||
        setweight(to_tsvector('russian', coalesce(new.address, '')), 'B') ||
        setweight(to_tsvector('russian', coalesce(company_name, '')), 'C');
    return new;
end;
$$;

create trigger trg_request_search_vector
    before insert or update of number, address, company_id on request
    for each row execute function update_request_search_vector();

create table service_offer (
    id               uuid        primary key default gen_random_uuid(),
    request_id       uuid        not null references request (id) on delete restrict,
    position         integer     not null default 1,
    status           text        not null default 'PROPOSED',
    tariff_type      text        not null default 'STANDARD',
    speed_mbps       integer,
    discount_percent numeric(5, 2),
    price_rub        numeric(14, 2),
    term_months      integer,
    contract_type    text        not null default 'OFFER',
    attrs            jsonb       not null default '{}',
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint ck_service_offer_status check (status in ('PROPOSED', 'SELECTED', 'DECLINED')),
    constraint ck_service_offer_tariff_type check (tariff_type in ('STANDARD', 'INDIVIDUAL')),
    constraint ck_service_offer_contract_type check (contract_type in ('OFFER', 'STANDARD', 'CLIENT_FORM')),
    constraint ck_service_offer_position check (position > 0),
    constraint ck_service_offer_speed check (speed_mbps is null or speed_mbps > 0),
    constraint ck_service_offer_discount check (discount_percent is null or discount_percent between 0 and 100),
    constraint ck_service_offer_price check (price_rub is null or price_rub >= 0),
    constraint ck_service_offer_term check (term_months is null or term_months > 0),
    constraint ux_service_offer_request_position unique (request_id, position)
);

create unique index ux_service_offer_selected on service_offer (request_id) where status = 'SELECTED';
create index ix_service_offer_request on service_offer (request_id);

alter table request
    add constraint fk_request_selected_offer
        foreign key (selected_offer_id) references service_offer (id) on delete restrict;

create table request_stage (
    id                  uuid        primary key default gen_random_uuid(),
    request_id          uuid        not null references request (id) on delete restrict,
    route_version       integer     not null default 0,
    version             bigint      not null default 0,
    type                text        not null,
    status              text        not null default 'QUEUED',
    queue_role          text,
    assignee_id         uuid        references app_user (id) on delete restrict,
    needs_reassignment  boolean     not null default false,
    required_by_rules   text[]      not null default '{}',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    taken_at            timestamptz,
    finished_at         timestamptz,
    constraint ck_request_stage_type check (type in (
        'TECH_CHECK', 'RECHECK_TECH', 'LEGAL_APPROVAL', 'FINANCE_APPROVAL',
        'PROCUREMENT_APPROVAL', 'MGMT_APPROVAL', 'CONTRACT_SIGNING', 'CONSTRUCTION',
        'INSTALLATION', 'ACTIVATION', 'REMEDIATION', 'CLOSING'
    )),
    constraint ck_request_stage_status check (status in (
        'QUEUED', 'IN_PROGRESS', 'DONE', 'RETURNED', 'REJECTED', 'SUPERSEDED', 'CANCELLED'
    )),
    constraint ck_request_stage_queue_role check (
        queue_role is null or queue_role in (
            'SALES_MANAGER', 'TECH_SPECIALIST', 'LAWYER', 'FINANCE_CONTROLLER',
            'PROCUREMENT_SPECIALIST', 'B2B_HEAD', 'ENGINEER', 'COORDINATOR'
        )
    ),
    constraint ck_request_stage_assignee_requires_progress check (
        assignee_id is null or status in ('IN_PROGRESS', 'DONE', 'RETURNED', 'REJECTED')
    )
);

create unique index ux_request_stage_active_type
    on request_stage (request_id, route_version, type) where status <> 'SUPERSEDED';
create index ix_request_stage_queue on request_stage (queue_role, status);
create index ix_request_stage_assignee on request_stage (assignee_id, status);
create index ix_request_stage_request on request_stage (request_id);

-- EC-18: согласующий не может быть менеджером этой же заявки.
-- Проверка в сервисе дублируется ограничением БД.
create or replace function check_stage_separation_of_duties() returns trigger
    language plpgsql
as $$
declare
    request_manager uuid;
begin
    if new.assignee_id is null
        or new.type not in ('LEGAL_APPROVAL', 'FINANCE_APPROVAL', 'PROCUREMENT_APPROVAL', 'MGMT_APPROVAL') then
        return new;
    end if;

    select manager_id into request_manager from request where id = new.request_id;

    if request_manager is not null and request_manager = new.assignee_id then
        raise exception 'EC-18: менеджер заявки не может исполнять согласование'
            using errcode = 'check_violation';
    end if;

    return new;
end;
$$;

create trigger trg_request_stage_separation
    before insert or update on request_stage
    for each row execute function check_stage_separation_of_duties();

create table request_revision (
    id              uuid        primary key default gen_random_uuid(),
    request_id      uuid        not null references request (id) on delete restrict,
    revision_no     integer     not null,
    reason          text        not null,
    created_by      uuid        not null references app_user (id) on delete restrict,
    created_at      timestamptz not null default now(),
    params_snapshot jsonb       not null default '{}',
    constraint ck_request_revision_reason check (reason in ('SUBMIT', 'REWORK', 'RECALC', 'RECHECK')),
    constraint ck_request_revision_no check (revision_no > 0),
    constraint ux_request_revision_number unique (request_id, revision_no)
);

create index ix_request_revision_request on request_revision (request_id);

create table audit_log (
    id             uuid        primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    correlation_id text,
    actor_id       uuid,
    actor_role     text,
    company_id     uuid,
    request_id     uuid,
    action         text        not null,
    entity_type    text        not null,
    entity_id      uuid,
    from_status    text,
    to_status      text,
    comment        text,
    details        jsonb       not null default '{}',
    constraint ck_audit_log_comment_length check (comment is null or char_length(comment) <= 4000)
);

create index ix_audit_log_request on audit_log (request_id, created_at);
create index ix_audit_log_company on audit_log (company_id, created_at);
create index ix_audit_log_actor on audit_log (actor_id, created_at);

-- Журнал только добавляет: приложению оставлены INSERT и SELECT (nfr.md, 5).
-- Роль создаёт infra/postgres/init, поэтому миграция её не создаёт и не падает,
-- если роли нет (например, в тестовом контейнере).
do $$
begin
    if exists (select 1 from pg_roles where rolname = '${app-user}') then
        execute format('revoke update, delete, truncate on table audit_log from %I', '${app-user}');
    end if;
end;
$$;

create table external_check (
    id          uuid        primary key default gen_random_uuid(),
    request_id  uuid        not null references request (id) on delete restrict,
    system      text        not null,
    status      text        not null,
    attempts    integer     not null default 1,
    checked_at  timestamptz not null,
    raw_payload jsonb       not null default '{}',
    created_at  timestamptz not null default now(),
    constraint ck_external_check_system check (system in ('CRM', 'BILLING', 'REGISTRY')),
    constraint ck_external_check_status check (status in ('OK', 'ERROR')),
    constraint ck_external_check_attempts check (attempts > 0)
);

create index ix_external_check_request on external_check (request_id, system);

create table idempotency_key (
    id              uuid        primary key default gen_random_uuid(),
    user_id         uuid        not null references app_user (id) on delete restrict,
    idempotency_key text        not null,
    endpoint        text        not null,
    request_hash    text        not null,
    response_status integer,
    response_body   jsonb,
    created_at      timestamptz not null default now(),
    expires_at      timestamptz not null,
    constraint ux_idempotency_key_user unique (user_id, idempotency_key)
);

create index ix_idempotency_key_expires on idempotency_key (expires_at);
