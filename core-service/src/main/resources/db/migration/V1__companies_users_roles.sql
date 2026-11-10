-- Компании, пользователи и роли.
-- Соглашения: docs/system/domain-model.md, раздел 8.
-- Первичные ключи — uuid с генерацией в БД, перечисления — text с CHECK,
-- внешние ключи — ON DELETE RESTRICT, время — timestamptz.

create table app_user (
    id            uuid        primary key default gen_random_uuid(),
    login         text        not null,
    password_hash text        not null,
    full_name     text        not null,
    email         text        not null,
    is_active     boolean     not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint ck_app_user_login_length check (char_length(login) between 3 and 100)
);

-- Логин уникален без учёта регистра.
create unique index ux_app_user_login on app_user (lower(login));

create table company (
    id            uuid        primary key default gen_random_uuid(),
    name          text        not null,
    inn           text        not null,
    kpp           text,
    ogrn          text,
    legal_address text,
    status        text        not null default 'PENDING',
    reject_reason text,
    confirmed_by  uuid        references app_user (id) on delete restrict,
    confirmed_at  timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint ck_company_status check (status in ('PENDING', 'ACTIVE', 'REJECTED')),
    constraint ck_company_inn_length check (char_length(inn) in (10, 12)),
    constraint ck_company_reject_reason check (status <> 'REJECTED' or reject_reason is not null)
);

-- EC-15: ИНН уникален среди активных компаний.
create unique index ux_company_inn_active on company (inn) where status = 'ACTIVE';
create index ix_company_status on company (status);

create table company_membership (
    id         uuid        primary key default gen_random_uuid(),
    company_id uuid        not null references company (id) on delete restrict,
    user_id    uuid        not null references app_user (id) on delete restrict,
    added_by   uuid        references app_user (id) on delete restrict,
    added_at   timestamptz not null default now(),
    removed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- EC-17: удалённый сотрудник остаётся в истории, но активная связь с компанией одна.
create unique index ux_company_membership_active
    on company_membership (company_id, user_id) where removed_at is null;
create index ix_company_membership_user on company_membership (user_id) where removed_at is null;

create table membership_role (
    membership_id uuid not null references company_membership (id) on delete restrict,
    role          text not null,
    primary key (membership_id, role),
    constraint ck_membership_role check (role in ('CLIENT_OWNER', 'CLIENT_ADMIN', 'CLIENT_REQUESTER'))
);

create table operator_role (
    user_id uuid not null references app_user (id) on delete restrict,
    role    text not null,
    primary key (user_id, role),
    constraint ck_operator_role check (role in (
        'SALES_MANAGER',
        'TECH_SPECIALIST',
        'LAWYER',
        'FINANCE_CONTROLLER',
        'PROCUREMENT_SPECIALIST',
        'B2B_HEAD',
        'ENGINEER',
        'COORDINATOR',
        'ADMIN'
    ))
);

-- Роль владельца единственная на компанию. Сервис проверяет это явно,
-- триггер закрывает гонку двух одновременных назначений.
create or replace function check_single_client_owner() returns trigger
    language plpgsql
as $$
declare
    target_company_id uuid;
begin
    if new.role <> 'CLIENT_OWNER' then
        return new;
    end if;

    select company_id into target_company_id
    from company_membership
    where id = new.membership_id;

    if exists (
        select 1
        from membership_role mr
        join company_membership cm on cm.id = mr.membership_id
        where mr.role = 'CLIENT_OWNER'
          and cm.company_id = target_company_id
          and cm.removed_at is null
          and mr.membership_id <> new.membership_id
    ) then
        raise exception 'У компании уже есть владелец (EC-17)'
            using errcode = 'unique_violation';
    end if;

    return new;
end;
$$;

create trigger trg_membership_role_single_owner
    before insert or update on membership_role
    for each row execute function check_single_client_owner();
