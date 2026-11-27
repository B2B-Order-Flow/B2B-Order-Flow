import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { queryKeys, stagesApi, usersApi } from '../../api/endpoints';
import { errorMessage, isApiError } from '../../api/errors';
import type { Role, StageView } from '../../api/types';
import { useNotify } from '../../components/Notifier';
import { Chip } from '../../components/StatusBadge';
import { Cell, DataTable, TwoLineCell, type Column } from '../../components/ui/DataTable';
import { SelectField } from '../../components/ui/FormField';
import { Notice } from '../../components/ui/Notice';
import { Muted, Page, PageTitle } from '../../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { formatShortDateTime } from '../../lib/format';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { byUrgency, StageSlaCell } from './StageSlaCell';

const t = texts.queue;

/** «по правилам R-01, R-03» — почему этап появился в маршруте (StageView.requiredByRules). */
function rulesLabel(stage: StageView): string | undefined {
  return stage.requiredByRules?.length ? texts.queue.byRules(stage.requiredByRules.join(', ')) : undefined;
}

/** Роли с очередью этапов (status-model.md §4.1; этапы менеджера в очередь не попадают). */
export const QUEUE_ROLES: Role[] = [
  'TECH_SPECIALIST',
  'ENGINEER',
  'LAWYER',
  'FINANCE_CONTROLLER',
  'PROCUREMENT_SPECIALIST',
  'B2B_HEAD',
];

// UI-11. Очередь роли (ui-requirements.md §7.1, макет «UI-11 Очередь роли»).
// Исполнитель видит очередь активной роли и берёт этап; координатор выбирает роль и назначает.
export function QueuePage() {
  const { activeRole } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const notify = useNotify();
  const coordinator = activeRole === 'COORDINATOR';
  const [coordinatorRole, setCoordinatorRole] = useState<Role>('TECH_SPECIALIST');
  const [assigning, setAssigning] = useState<StageView | null>(null);
  const queueRole: Role = coordinator ? coordinatorRole : (activeRole ?? 'TECH_SPECIALIST');

  const listQuery = { queueRole, status: 'QUEUED' as const, size: 100 };
  const query = useQuery({ queryKey: queryKeys.stagesQueue(listQuery), queryFn: () => stagesApi.queue(listQuery) });

  const claim = useMutation({
    mutationFn: (stage: StageView) => stagesApi.claim(stage.id, stage.version),
    onSuccess: (_, stage) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.stagesAll });
      // Карточка берёт этап из заявки: кэш со старым статусом этапа показал бы кнопку «Взять» ещё раз
      queryClient.removeQueries({ queryKey: queryKeys.request(stage.requestId) });
      notify.success(t.claimed);
      void navigate(`/requests/${stage.requestId}`);
    },
    onError: (error) => {
      // Этап уже взял кто-то другой — обновляем очередь (ui-requirements.md §10)
      if (
        isApiError(error) &&
        ['VERSION_CONFLICT', 'ILLEGAL_TRANSITION', 'STAGE_ALREADY_DECIDED'].includes(error.code)
      ) {
        notify.info(t.alreadyTaken);
        void queryClient.invalidateQueries({ queryKey: queryKeys.stagesAll });
      } else notify.error(error);
    },
  });

  const rows = [...(query.data?.content ?? [])].sort(byUrgency);

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
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <TwoLineCell title={texts.stageType[s.type]} subtitle={rulesLabel(s)} />
          {coordinator && s.needsReassignment ? <Chip tone="orange" label={t.needsReassignment} /> : null}
        </Box>
      ),
    },
    {
      key: 'queued',
      title: t.columns.queued,
      width: 130,
      render: (s) => <Cell>{formatShortDateTime(s.createdAt)}</Cell>,
    },
    { key: 'sla', title: t.columns.sla, width: 300, render: (s) => <StageSlaCell stage={s} /> },
    {
      key: 'action',
      title: '',
      width: 110,
      render: (s) =>
        coordinator ? (
          <Button variant="outlined" onClick={() => setAssigning(s)} data-testid="action-assign">
            {t.assign}
          </Button>
        ) : (
          <Button
            variant="contained"
            loading={claim.isPending && claim.variables?.id === s.id}
            disabled={claim.isPending}
            onClick={() => claim.mutate(s)}
            data-testid="action-claim"
          >
            {t.claim}
          </Button>
        ),
    },
  ];

  return (
    <Page gap={22} top={28} testId="queue-page">
      <PageTitle>{t.title(t.queueNames[queueRole] ?? texts.roles[queueRole])}</PageTitle>
      <Muted>{t.intro}</Muted>
      {coordinator ? (
        <Box sx={{ maxWidth: 360 }}>
          <SelectField
            label={t.role}
            value={coordinatorRole}
            onChange={(value) => setCoordinatorRole(value as Role)}
            options={QUEUE_ROLES.map((role) => ({ value: role, label: texts.roles[role] }))}
            testId="queue-role"
          />
        </Box>
      ) : null}
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState text={t.empty} />
      ) : (
        <DataTable
          label={t.title(t.queueNames[queueRole] ?? '')}
          columns={columns}
          rows={rows}
          rowKey={(s) => s.id}
          gap={20}
          rowPaddingY={16}
          rowTestId={(s) => `queue-row-${s.requestNumber ?? s.id}`}
          testId="queue-table"
        />
      )}
      {assigning ? (
        <AssignStageDialog
          stage={assigning}
          role={assigning.queueRole ?? queueRole}
          onClose={() => setAssigning(null)}
        />
      ) : null}
    </Page>
  );
}

/** «Назначить» (координатор): сотрудник с ролью очереди этапа (GET /users?role=…, открытый вопрос 3). */
function AssignStageDialog({ stage, role, onClose }: { stage: StageView; role: Role; onClose: () => void }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [assigneeId, setAssigneeId] = useState('');
  const users = useQuery({
    queryKey: queryKeys.usersByRole(role),
    queryFn: () => usersApi.byRole(role),
  });
  const assign = useMutation({
    mutationFn: () => stagesApi.assign(stage.id, assigneeId, stage.version),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.stagesAll });
      void queryClient.invalidateQueries({ queryKey: queryKeys.request(stage.requestId) });
      notify.success(t.assigned);
      onClose();
    },
  });
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t.assignTitle(stage.requestNumber ?? '')}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '12px', pt: '4px' }}>
          {users.isError ? (
            <Notice tone="error">{errorMessage(users.error)}</Notice>
          ) : (
            <SelectField
              label={t.assignee}
              required
              value={assigneeId}
              onChange={setAssigneeId}
              disabled={users.isPending}
              placeholder={users.isPending ? texts.common.loading : t.chooseAssignee}
              options={(users.data?.content ?? [])
                .filter((u) => u.active)
                .map((u) => ({ value: u.id, label: `${u.fullName} (${u.login})` }))}
              testId="stage-assignee"
            />
          )}
          {assign.isError ? <Notice tone="error">{errorMessage(assign.error, { mutation: true })}</Notice> : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button variant="contained" disabled={!assigneeId} loading={assign.isPending} onClick={() => assign.mutate()}>
          {t.assign}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
