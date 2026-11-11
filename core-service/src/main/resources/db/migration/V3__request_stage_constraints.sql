-- Исправления ограничений request_stage по замечаниям ревью к V2.
-- Применённую V2 не правим: изменения идут отдельной миграцией.

-- Исполнитель запрещён только у QUEUED. При отмене (T08, T19) и пересборке
-- маршрута взятый этап закрывается в CANCELLED или SUPERSEDED, но исполнитель
-- должен сохраниться в истории (status-model.md, 4.2).
alter table request_stage
    drop constraint ck_request_stage_assignee_requires_progress;

alter table request_stage
    add constraint ck_request_stage_assignee_requires_progress
        check (assignee_id is null or status <> 'QUEUED');

-- Один активный этап типа на route_version. RETURNED и CANCELLED — уже снятые
-- с маршрута записи: после возврата T09b и повторной отправки T10 этап того же
-- типа должен создаваться заново. route_version увеличивается при каждой
-- пересборке маршрута (T06, T10, T15b).
drop index ux_request_stage_active_type;

create unique index ux_request_stage_active_type
    on request_stage (request_id, route_version, type)
    where status not in ('SUPERSEDED', 'RETURNED', 'CANCELLED');
