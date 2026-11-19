-- Demo-only users, companies and memberships.
-- All demo accounts use the same BCrypt-10 hash. The value is supplied by DEMO_PASSWORD in test tooling.

insert into app_user (login, password_hash, full_name, email) values
    ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Системный администратор', 'admin@demo.test'),
    ('a.smirnova', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Анна Смирнова', 'a.smirnova@demo.test'),
    ('p.orlov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Павел Орлов', 'p.orlov@demo.test'),
    ('i.kim', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Игорь Ким', 'i.kim@demo.test'),
    ('e.volkova', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Елена Волкова', 'e.volkova@demo.test'),
    ('s.belov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Сергей Белов', 's.belov@demo.test'),
    ('o.zaitseva', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Ольга Зайцева', 'o.zaitseva@demo.test'),
    ('m.gromov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Михаил Громов', 'm.gromov@demo.test'),
    ('a.lebedev', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Артём Лебедев', 'a.lebedev@demo.test'),
    ('m.kovaleva', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Мария Ковалёва', 'm.kovaleva@demo.test'),
    ('a.sokolov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Алексей Соколов', 'a.sokolov@demo.test'),
    ('n.fedorova', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Наталья Фёдорова', 'n.fedorova@demo.test'),
    ('v.romanov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Виктор Романов', 'v.romanov@demo.test'),
    ('t.morozova', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Татьяна Морозова', 't.morozova@demo.test'),
    ('o.nikitin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Олег Никитин', 'o.nikitin@demo.test'),
    ('i.pavlova', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Ирина Павлова', 'i.pavlova@demo.test'),
    ('g.tikhonov', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Глеб Тихонов', 'g.tikhonov@demo.test'),
    ('owner.severtorg', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец Севертoрга', 'owner.severtorg@demo.test'),
    ('owner.gbu7', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец больницы № 7', 'owner.gbu7@demo.test'),
    ('owner.timeout', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец Таймаута', 'owner.timeout@demo.test'),
    ('owner.slow', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец Медленного', 'owner.slow@demo.test'),
    ('owner.flaky', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец Шаткого', 'owner.flaky@demo.test'),
    ('owner.deaf', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Владелец Глухого', 'owner.deaf@demo.test');

insert into operator_role (user_id, role)
select id, role
from app_user
cross join (values
    ('admin', 'ADMIN'),
    ('a.smirnova', 'SALES_MANAGER'),
    ('p.orlov', 'TECH_SPECIALIST'),
    ('i.kim', 'TECH_SPECIALIST'),
    ('i.kim', 'ENGINEER'),
    ('e.volkova', 'LAWYER'),
    ('s.belov', 'FINANCE_CONTROLLER'),
    ('o.zaitseva', 'PROCUREMENT_SPECIALIST'),
    ('m.gromov', 'B2B_HEAD'),
    ('a.lebedev', 'ENGINEER'),
    ('m.kovaleva', 'COORDINATOR')
) as roles(login, role)
where app_user.login = roles.login
on conflict do nothing;

insert into company (name, inn, status) values
    ('ИП Соколов А. В.', '770112345127', 'ACTIVE'),
    ('ООО «Логистик Плюс»', '7725012341', 'ACTIVE'),
    ('АО «Агрохолдинг Заря»', '5050123455', 'ACTIVE'),
    ('ООО «Вектор»', '7811001128', 'PENDING'),
    ('ООО «Севертoрг»', '7844002230', 'ACTIVE'),
    ('ГБУ «Городская больница № 7»', '7719003349', 'ACTIVE'),
    ('ООО «Таймаут»', '9999005565', 'ACTIVE'),
    ('ООО «Медленный»', '9999000052', 'ACTIVE'),
    ('ООО «Шаткий»', '9999000013', 'ACTIVE'),
    ('ООО «Глухой»', '9999000020', 'ACTIVE');

insert into company_membership (company_id, user_id)
select c.id, u.id
from company c
join app_user u on (c.inn, u.login) in (
    ('770112345127', 'a.sokolov'),
    ('7725012341', 'n.fedorova'),
    ('7725012341', 'v.romanov'),
    ('5050123455', 't.morozova'),
    ('5050123455', 'o.nikitin'),
    ('5050123455', 'i.pavlova'),
    ('7811001128', 'g.tikhonov'),
    ('7844002230', 'owner.severtorg'),
    ('7719003349', 'owner.gbu7'),
    ('9999005565', 'owner.timeout'),
    ('9999000052', 'owner.slow'),
    ('9999000013', 'owner.flaky'),
    ('9999000020', 'owner.deaf')
);

insert into membership_role (membership_id, role)
select cm.id, roles.role
from company_membership cm
join company c on c.id = cm.company_id
join app_user u on u.id = cm.user_id
join (values
    ('770112345127', 'a.sokolov', 'CLIENT_OWNER'),
    ('7725012341', 'n.fedorova', 'CLIENT_OWNER'),
    ('7725012341', 'v.romanov', 'CLIENT_REQUESTER'),
    ('5050123455', 't.morozova', 'CLIENT_OWNER'),
    ('5050123455', 'o.nikitin', 'CLIENT_ADMIN'),
    ('5050123455', 'i.pavlova', 'CLIENT_REQUESTER'),
    ('7811001128', 'g.tikhonov', 'CLIENT_OWNER'),
    ('7844002230', 'owner.severtorg', 'CLIENT_OWNER'),
    ('7719003349', 'owner.gbu7', 'CLIENT_OWNER'),
    ('9999005565', 'owner.timeout', 'CLIENT_OWNER'),
    ('9999000052', 'owner.slow', 'CLIENT_OWNER'),
    ('9999000013', 'owner.flaky', 'CLIENT_OWNER'),
    ('9999000020', 'owner.deaf', 'CLIENT_OWNER')
) as roles(inn, login, role)
    on roles.inn = c.inn and roles.login = u.login
on conflict do nothing;

-- Normalize the company and owner spelling to the seed specification.
update company
set name = replace(name, 'o', chr(1086))
where inn = '7844002230';

update app_user
set full_name = replace(full_name, 'o', chr(1086))
where login = 'owner.severtorg';
