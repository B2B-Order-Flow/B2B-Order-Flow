import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { queryKeys, requestsApi } from '../api/endpoints';
import type { RequestListQuery, RequestStatus, RequestView } from '../api/types';
import { IntegrationErrorBadge, StatusBadge } from '../components/StatusBadge';
import { Cell, DataTable, type Column } from '../components/ui/DataTable';
import { Page, PageTitle } from '../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatDate, formatSpeed } from '../lib/format';
import { clientActionHint } from '../lib/requestView';
import { canCreateRequest, isCompanyRole } from '../session/roles';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { c, radius, textStyles } from '../theme/tokens';

const t = texts.requests;
const PAGE_SIZE = 20;

type FilterKey = 'all' | 'inWork' | 'action' | 'done';

const FILTER_STATUSES: Record<FilterKey, RequestStatus[] | undefined> = {
  all: undefined,
  inWork: [
    'DATA_CHECK',
    'WITH_MANAGER',
    'TECH_CHECK',
    'IN_APPROVAL',
    'REWORK',
    'CONTRACT_SIGNING',
    'IN_EXECUTION',
    'ACTIVATED',
    'CANCEL_REQUESTED',
  ],
  // Клиенту — заявки, где ждут его действия (ui-requirements.md §6.5)
  action: ['CONTRACT_SIGNING', 'ACTIVATED'],
  done: ['CLOSED', 'REJECTED', 'CANCELLED'],
};

// UI-05. Заявки (ui-requirements.md §6.5, макет «UI-05 Заявки»). Что попадает в список, решает ядро.
export function RequestsPage() {
  const { activeRole, company } = useSession();
  const navigate = useNavigate();
  const client = isCompanyRole(activeRole);
  const [filter, setFilter] = useState<FilterKey>('all');
  const [page, setPage] = useState(0);

  const listQuery: RequestListQuery = {
    page,
    size: PAGE_SIZE,
    sort: 'createdAt,desc',
    status: FILTER_STATUSES[filter],
  };
  const query = useQuery({
    queryKey: queryKeys.requests(listQuery),
    queryFn: () => requestsApi.list(listQuery),
    placeholderData: keepPreviousData,
  });

  const canCreate = canCreateRequest(activeRole);
  const companyActive = company?.status === 'ACTIVE';
  const filters: FilterKey[] = client ? ['all', 'inWork', 'action', 'done'] : ['all', 'inWork', 'done'];

  const createButton = canCreate ? (
    <Tooltip title={companyActive ? '' : texts.session.companyPending}>
      <span>
        <Button
          variant="contained"
          disabled={!companyActive}
          onClick={() => void navigate('/requests/new')}
          data-testid="requests-create"
        >
          {t.create}
        </Button>
      </span>
    </Tooltip>
  ) : null;

  const columns: Column<RequestView>[] = [
    {
      key: 'number',
      title: t.columns.number,
      width: client ? 180 : 170,
      render: (r) => <Cell strong>{r.number}</Cell>,
    },
    ...(client
      ? []
      : [
          {
            key: 'company',
            title: t.columns.company,
            width: 200,
            render: (r: RequestView) => <Cell>{r.companyName ?? '—'}</Cell>,
          },
        ]),
    { key: 'address', title: t.columns.address, render: (r) => <Cell>{r.address ?? '—'}</Cell> },
    {
      key: 'speed',
      title: t.columns.speed,
      width: client ? 110 : 100,
      render: (r) => <Cell>{formatSpeed(r.requestedSpeedMbps)}</Cell>,
    },
    {
      key: 'status',
      title: t.columns.status,
      width: client ? 300 : 260,
      render: (r) => <StatusCell request={r} client={client} />,
    },
    {
      key: 'submitted',
      title: t.columns.submitted,
      width: 110,
      render: (r) => <Cell>{r.submittedAt ? formatDate(r.submittedAt) : '—'}</Cell>,
    },
  ];

  const data = query.data;
  const total = data?.page.totalElements ?? 0;
  const from = data && total > 0 ? data.page.number * data.page.size + 1 : 0;
  const to = data ? from + data.content.length - 1 : 0;

  return (
    <Page testId="requests-page">
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <PageTitle>{activeRole === 'SALES_MANAGER' ? texts.menu.myRequests : t.title}</PageTitle>
          {client && company ? (
            <Typography variant="body1" sx={{ color: c('text-muted') }}>
              {company.name}
            </Typography>
          ) : null}
        </Box>
        {createButton}
      </Box>

      <Box sx={{ display: 'flex', gap: '8px' }} role="group" aria-label={t.filtersLabel}>
        {filters.map((key) => {
          const active = key === filter;
          return (
            <ButtonBase
              key={key}
              aria-pressed={active}
              data-testid={`requests-filter-${key}`}
              onClick={() => {
                setFilter(key);
                setPage(0);
              }}
              sx={{
                ...(active ? textStyles.accent : textStyles.body),
                px: '14px',
                py: '8px',
                borderRadius: `${radius.full}px`,
                color: active ? c('action-primary-text') : c('text-primary'),
                bgcolor: active ? c('action-primary-soft') : 'transparent',
                boxShadow: active ? 'none' : `inset 0 0 0 1px ${c('border-strong')}`,
              }}
            >
              {t.filters[key]}
            </ButtonBase>
          );
        })}
      </Box>

      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          text={client && filter === 'all' ? t.emptyClient : t.emptyOperator}
          action={filter === 'all' ? createButton : undefined}
        />
      ) : data ? (
        <>
          <DataTable
            label={t.title}
            columns={columns}
            rows={data.content}
            rowKey={(r) => r.id}
            rowTestId={(r) => `requests-row-${r.number}`}
            testId="requests-table"
            onRowClick={(r) =>
              void navigate(r.status === 'DRAFT' && canCreate ? `/requests/${r.id}/edit` : `/requests/${r.id}`)
            }
          />
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Typography variant="caption" sx={{ color: c('text-muted') }} data-testid="requests-shown">
              {t.shown(from, to, total)}
            </Typography>
            {data.page.totalPages > 1 ? (
              <>
                <Button variant="text" sx={{ height: 32 }} disabled={page === 0} onClick={() => setPage(page - 1)}>
                  {t.prev}
                </Button>
                <Button
                  variant="text"
                  sx={{ height: 32 }}
                  disabled={page + 1 >= data.page.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  {t.next}
                </Button>
              </>
            ) : null}
          </Box>
        </>
      ) : null}
    </Page>
  );
}

function StatusCell({ request, client }: { request: RequestView; client: boolean }) {
  const hint = client ? clientActionHint(request) : null;
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}>
      <Box sx={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        <StatusBadge status={request.status} />
        {!client && request.integrationError ? <IntegrationErrorBadge /> : null}
      </Box>
      {hint ? (
        <Typography variant="caption" sx={{ color: c('status-ochre-fg') }} data-testid="request-action-hint">
          {hint}
        </Typography>
      ) : client && request.integrationError ? (
        <Typography variant="caption" sx={{ color: c('text-muted') }}>
          {texts.integrationError.client}
        </Typography>
      ) : null}
    </Box>
  );
}
