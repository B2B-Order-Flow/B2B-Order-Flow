import Button from '@mui/material/Button';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { queryKeys, stagesApi } from '../../api/endpoints';
import type { StageView } from '../../api/types';
import { Cell, DataTable, TwoLineCell, type Column } from '../../components/ui/DataTable';
import { Muted, Page, PageTitle } from '../../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { formatShortDateTime } from '../../lib/format';
import { canAccess, routeAccess } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { StageSlaCell } from './StageSlaCell';

const t = texts.myTasks;

// UI-12. Мои задачи (ui-requirements.md §7.2, макет «UI-12 Мои задачи»).
export function MyTasksPage() {
  const { activeRole } = useSession();
  const navigate = useNavigate();
  const query = useQuery({ queryKey: queryKeys.stagesMy, queryFn: stagesApi.my });
  const open = (stage: StageView) => void navigate(`/requests/${stage.requestId}`);

  // Только этапы в работе: у этапа QUEUED в карточке нет формы действия. Порядок — от ядра,
  // оно сортирует «Мои задачи» по остатку SLA (core-api.yaml, /stages/my)
  const rows = (query.data?.content ?? []).filter((s) => s.status === 'IN_PROGRESS');

  const columns: Column<StageView>[] = [
    {
      key: 'request',
      title: t.columns.request,
      width: 170,
      render: (s) => <Cell strong>{s.requestNumber ?? '—'}</Cell>,
    },
    {
      key: 'stage',
      title: t.columns.stage,
      render: (s) => (
        <TwoLineCell
          title={texts.stageType[s.type]}
          subtitle={s.takenAt ? t.taken(formatShortDateTime(s.takenAt)) : undefined}
        />
      ),
    },
    { key: 'sla', title: t.columns.sla, width: 300, render: () => <StageSlaCell /> },
    {
      key: 'action',
      title: '',
      width: 120,
      render: (s) => (
        <Button
          variant="outlined"
          onClick={(event) => {
            event.stopPropagation();
            open(s);
          }}
          data-testid="task-open"
        >
          {t.open}
        </Button>
      ),
    },
  ];

  const canSeeQueue = canAccess(routeAccess.queue, activeRole);

  return (
    <Page gap={22} top={28} testId="my-tasks-page">
      <PageTitle>{t.title}</PageTitle>
      <Muted>{t.intro}</Muted>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          text={t.empty}
          action={
            canSeeQueue ? (
              <Button variant="outlined" onClick={() => void navigate('/queue')}>
                {t.toQueue}
              </Button>
            ) : undefined
          }
        />
      ) : (
        <DataTable
          label={t.title}
          columns={columns}
          rows={rows}
          rowKey={(s) => s.id}
          gap={20}
          rowPaddingY={16}
          onRowClick={open}
          rowTestId="task-row"
          testId="tasks-table"
        />
      )}
    </Page>
  );
}
