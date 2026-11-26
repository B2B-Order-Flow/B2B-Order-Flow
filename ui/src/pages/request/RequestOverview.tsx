import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { RequestView } from '../../api/types';
import { ErrorState, LoadingState } from '../../components/states';
import { Notice } from '../../components/ui/Notice';
import { Columns, KeyValueGrid, Panel, PanelTitle } from '../../components/ui/Panel';
import { formatDate, formatMoney } from '../../lib/format';
import { contractTypeOf } from '../../lib/requestView';
import { isCompanyRole } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, radius } from '../../theme/tokens';
import { RouteStages } from './RouteStages';
import { actionLabel, actorLabel, dateAt, speedLabel } from './labels';
import { RevisionsPanel } from './RevisionsPanel';
import { RequestSlaPanel } from './SlaPanel';

const t = texts.requestCard;

// Обзор заявки (макет «UI-08 Карточка заявки (EC-10)»): плашка ошибки проверки, факты,
// путь заявки, история и параметры. Клиенту — без внутренних данных (ui-requirements.md §6.8).
export function RequestOverview({ request }: { request: RequestView }) {
  const { activeRole } = useSession();
  const client = isCompanyRole(activeRole);
  const level = request.commitmentLevel ?? 0;

  return (
    <>
      {request.integrationError ? (
        client ? (
          <Notice tone="waiting" size="lg" testId="integration-error-banner">
            {texts.integrationError.client}
          </Notice>
        ) : (
          <Notice tone="warning" size="lg" title={t.integrationTitle} testId="integration-error-banner">
            {t.integrationText}
          </Notice>
        )
      ) : null}

      <Box sx={{ display: 'flex', gap: '12px' }} data-testid="request-card-facts">
        <Fact label={t.speed} value={speedLabel(request)} />
        <Fact
          label={t.now}
          value={texts.requestStatus[request.status].label}
          sub={request.status === 'DATA_CHECK' && !client ? answeredLabel(request) : statusHint(request, client)}
        />
        <Fact label={t.desiredDate} value={formatDate(request.desiredDate)} />
        <Fact label={t.commitment} value={t.level(level)} sub={texts.commitmentLevel[level] ?? ''} />
      </Box>

      <RequestSlaPanel request={request} />

      <Columns>
        <RoutePanel request={request} client={client} />
        <Stack sx={{ flex: 1, gap: '24px', minWidth: 0 }}>
          <HistoryPanel request={request} />
          <RevisionsPanel request={request} />
          <Panel gap={14}>
            <PanelTitle>{t.params}</PanelTitle>
            <KeyValueGrid
              items={[
                { label: t.contract, value: contractLabel(request) },
                ...(request.contractNumber ? [{ label: t.contractNumber, value: request.contractNumber }] : []),
                { label: t.contact, value: request.contactName ?? '—' },
                { label: t.phone, value: request.contactPhone ?? '—' },
                ...(request.clientComment ? [{ label: t.comment, value: request.clientComment }] : []),
              ]}
            />
          </Panel>
        </Stack>
      </Columns>
    </>
  );
}

function contractLabel(request: RequestView): string {
  const type = contractTypeOf(request);
  return type ? texts.contractType[type] : '—';
}

function statusHint(request: RequestView, client: boolean): string {
  const hint = texts.requestStatus[request.status];
  return (client ? hint.client : hint.operator) || '';
}

function Fact({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Stack
      sx={{
        flex: 1,
        minWidth: 0,
        gap: '4px',
        px: '18px',
        py: '16px',
        bgcolor: c('bg-surface'),
        border: `1px solid ${c('border-default')}`,
        borderRadius: `${radius.md}px`,
      }}
    >
      <Typography variant="caption" sx={{ color: c('text-muted') }}>
        {label}
      </Typography>
      <Typography variant="h2" component="div">
        {value}
      </Typography>
      {sub ? (
        <Typography variant="caption" sx={{ color: c('text-muted') }}>
          {sub}
        </Typography>
      ) : null}
    </Stack>
  );
}

// Внешние системы (integrations.md): ответ виден по заполненным полям заявки.
interface SystemCheck {
  name: string;
  answered: boolean;
  result: string;
}

function systemChecks(request: RequestView): SystemCheck[] {
  const all = !request.integrationError;
  const pending = request.integrationError ? t.checks.noAnswer : t.checks.waiting;
  const registry = all || request.isGovCustomer !== undefined;
  const billing = all || request.debtAmount !== undefined;
  const crm = all || request.segment !== undefined || request.crmManagerId !== undefined;
  const debt = Number(request.debtAmount ?? 0);
  return [
    { name: t.checks.registry, answered: registry, result: registry ? t.checks.registryOk : pending },
    {
      name: t.checks.billing,
      answered: billing,
      result: billing ? (debt > 0 ? t.checks.debt(formatMoney(request.debtAmount)) : t.checks.noDebt) : pending,
    },
    {
      name: t.checks.crm,
      answered: crm,
      result: crm
        ? request.segment
          ? t.checks.segment(texts.segment[request.segment] ?? request.segment)
          : t.checks.crmOk
        : pending,
    },
  ];
}

function answeredLabel(request: RequestView): string {
  const checks = systemChecks(request);
  return t.answered(checks.filter((check) => check.answered).length, checks.length);
}

// Путь заявки: проверка данных и фактические этапы маршрута (RequestView.stages).
function RoutePanel({ request, client }: { request: RequestView; client: boolean }) {
  return (
    <Panel gap={14} sx={{ flex: 1 }} data-testid="request-card-route">
      <PanelTitle>{t.route}</PanelTitle>
      {request.status === 'DATA_CHECK' && !client ? <ChecksBlock request={request} /> : null}
      <RouteStages request={request} client={client} />
    </Panel>
  );
}

function ChecksBlock({ request }: { request: RequestView }) {
  return (
    <Stack sx={{ gap: '10px', px: '16px', py: '14px', bgcolor: c('bg-surface-2'), borderRadius: `${radius.md}px` }}>
      <Typography variant="subtitle1">{t.checks.title}</Typography>
      {systemChecks(request).map((check) => (
        <Box key={check.name} sx={{ display: 'flex', alignItems: 'center', gap: '10px' }} data-testid="system-check">
          <Box
            aria-hidden
            sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              flexShrink: 0,
              bgcolor: c(check.answered ? 'sla-ok-bar' : request.integrationError ? 'sla-breach-bar' : 'sla-paused'),
            }}
          />
          <Typography variant="body1" sx={{ flex: 1 }}>
            {check.name}
          </Typography>
          <Typography
            variant="subtitle2"
            sx={{
              color: c(check.answered ? 'status-green-fg' : request.integrationError ? 'status-red-fg' : 'text-muted'),
            }}
          >
            {check.result}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}

const HISTORY_PAGE = 10;

function HistoryPanel({ request }: { request: RequestView }) {
  const { user } = useSession();
  const [size, setSize] = useState(HISTORY_PAGE);
  const historyQuery = { page: 0, size, sort: 'createdAt,desc' };
  const query = useQuery({
    queryKey: queryKeys.history(request.id, historyQuery),
    queryFn: () => requestsApi.history(request.id, historyQuery),
    placeholderData: (previous) => previous,
  });

  return (
    <Panel gap={14} data-testid="request-card-history">
      <PanelTitle>{t.history}</PanelTitle>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <Typography variant="body1" sx={{ color: c('text-muted') }}>
          {request.status === 'DRAFT' ? t.historyDraft : t.historyEmpty}
        </Typography>
      ) : (
        <>
          {query.data.content.map((entry) => (
            <Stack key={entry.id} sx={{ gap: '2px' }} data-testid="history-entry">
              <Typography variant="body1" sx={{ color: c('text-secondary') }}>
                <Box component="span" sx={{ fontWeight: 600, color: c('text-primary') }}>
                  {actorLabel(entry, user)}
                </Box>{' '}
                {actionLabel(entry)}
              </Typography>
              <Typography variant="caption" sx={{ color: c('text-muted') }}>
                {[dateAt(entry.createdAt, false), entry.comment].filter(Boolean).join(', ')}
              </Typography>
            </Stack>
          ))}
          {query.data.page.totalElements > query.data.content.length ? (
            <Box>
              <Button variant="text" sx={{ height: 32, px: '8px' }} onClick={() => setSize(size + HISTORY_PAGE)}>
                {t.historyMore}
              </Button>
            </Box>
          ) : null}
        </>
      )}
    </Panel>
  );
}
