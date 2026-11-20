import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { queryKeys, requestsApi } from '../api/endpoints';
import { isApiError } from '../api/errors';
import type { RequestView } from '../api/types';
import { useNotify } from '../components/Notifier';
import { IntegrationErrorBadge } from '../components/StatusBadge';
import { Cell, DataTable, TwoLineCell, type Column } from '../components/ui/DataTable';
import { Muted, Page, PageTitle } from '../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatSpeed } from '../lib/format';
import { formatDuration } from '../lib/sla';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { AssignManagerDialog } from './request/RequestHeader';

const t = texts.managerQueue;
const LIST_QUERY = { status: 'DATA_CHECK' as const, unassigned: true, size: 100, sort: 'submittedAt,asc' };

/** Сколько заявка ждёт с момента отправки, календарное время. */
function waiting(request: RequestView): string {
  if (!request.submittedAt) return '—';
  return formatDuration((Date.now() - Date.parse(request.submittedAt)) / 60_000, false);
}

// UI-09. Очередь менеджеров (ui-requirements.md §6.9). Отдельного макета нет — собрана из тех же
// элементов, что UI-11: таблица, ячейка «компания и адрес», кнопки действий.
export function ManagerQueuePage() {
  const { activeRole } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const notify = useNotify();
  const coordinator = activeRole === 'COORDINATOR';
  const [assigning, setAssigning] = useState<RequestView | null>(null);

  const query = useQuery({ queryKey: queryKeys.requests(LIST_QUERY), queryFn: () => requestsApi.list(LIST_QUERY) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.requestsAll });

  const stale = (error: unknown) =>
    isApiError(error) && ['VERSION_CONFLICT', 'ILLEGAL_TRANSITION'].includes(error.code);

  const claim = useMutation({
    mutationFn: (request: RequestView) => requestsApi.claim(request.id, request.version),
    onSuccess: (result) => {
      void refresh();
      notify.success(t.claimed);
      void navigate(`/requests/${result.id}`);
    },
    onError: (error) => {
      if (stale(error)) notify.info(t.alreadyTaken);
      else notify.error(error);
      void refresh();
    },
  });

  const recheck = useMutation({
    mutationFn: (request: RequestView) => requestsApi.recheckData(request.id, request.version),
    onSuccess: () => {
      notify.success(texts.requestCard.recheckStarted);
      void refresh();
    },
    onError: (error) => {
      notify.error(error);
      void refresh();
    },
  });

  const columns: Column<RequestView>[] = [
    { key: 'number', title: t.columns.number, width: 170, render: (r) => <Cell strong>{r.number}</Cell> },
    {
      key: 'company',
      title: t.columns.company,
      render: (r) => (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}>
          <TwoLineCell title={r.companyName ?? '—'} subtitle={r.address} />
          {r.integrationError ? <IntegrationErrorBadge /> : null}
        </Box>
      ),
    },
    {
      key: 'segment',
      title: t.columns.segment,
      width: 120,
      render: (r) => <Cell>{r.segment ? (texts.segment[r.segment] ?? r.segment) : '—'}</Cell>,
    },
    {
      key: 'speed',
      title: t.columns.speed,
      width: 100,
      render: (r) => <Cell>{formatSpeed(r.requestedSpeedMbps)}</Cell>,
    },
    { key: 'waiting', title: t.columns.waiting, width: 120, render: (r) => <Cell>{waiting(r)}</Cell> },
    {
      key: 'actions',
      title: '',
      width: coordinator ? 400 : 110,
      render: (r) => (
        // Кнопки в строке не открывают карточку: клик по строке — переход, по кнопке — действие
        <Box
          sx={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}
          onClick={(event) => event.stopPropagation()}
        >
          {!coordinator && !r.integrationError ? (
            <Button
              variant="contained"
              disabled={claim.isPending}
              loading={claim.isPending && claim.variables?.id === r.id}
              onClick={() => claim.mutate(r)}
              data-testid="action-claim"
            >
              {t.claim}
            </Button>
          ) : null}
          {coordinator ? (
            <Button variant="outlined" onClick={() => setAssigning(r)} data-testid="action-assign-manager">
              {texts.requestCard.assignManager}
            </Button>
          ) : null}
          {coordinator && r.integrationError ? (
            <Button
              variant="contained"
              loading={recheck.isPending && recheck.variables?.id === r.id}
              onClick={() => recheck.mutate(r)}
              data-testid="action-recheck-data"
            >
              {texts.requestCard.recheck}
            </Button>
          ) : null}
        </Box>
      ),
    },
  ];

  return (
    <Page gap={22} top={28} testId="manager-queue">
      <PageTitle>{t.title}</PageTitle>
      <Muted>{t.intro}</Muted>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState text={t.empty} />
      ) : (
        <DataTable
          label={t.title}
          columns={columns}
          rows={query.data.content}
          rowKey={(r) => r.id}
          gap={20}
          rowPaddingY={16}
          onRowClick={(r) => void navigate(`/requests/${r.id}`)}
          rowTestId={(r) => `manager-queue-row-${r.number}`}
        />
      )}
      {assigning ? <AssignManagerDialog request={assigning} onClose={() => setAssigning(null)} /> : null}
    </Page>
  );
}
