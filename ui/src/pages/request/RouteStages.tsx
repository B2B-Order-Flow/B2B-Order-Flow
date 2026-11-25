import BlockIcon from '@mui/icons-material/Block';
import CheckIcon from '@mui/icons-material/Check';
import ReplayIcon from '@mui/icons-material/Replay';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { RequestStatus, RequestView, RouteStageView, StageStatus, StageType, StageView } from '../../api/types';
import { APPROVAL_STAGES, DECIDED_STATUSES } from '../../lib/approvals';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, type ColorToken } from '../../theme/tokens';

const t = texts.requestCard;

const BEFORE_ROUTE: ReadonlySet<RequestStatus> = new Set(['DRAFT', 'DATA_CHECK', 'WITH_MANAGER']);
const FINAL: ReadonlySet<RequestStatus> = new Set(['CLOSED', 'REJECTED', 'CANCELLED']);

type RowState = StageStatus | 'PLANNED';

const MARK: Record<RowState, { ring: ColorToken; fill?: boolean; dashed?: boolean; icon?: ReactNode }> = {
  QUEUED: { ring: 'border-strong' },
  IN_PROGRESS: { ring: 'action-primary' },
  DONE: { ring: 'sla-ok-bar', fill: true, icon: <CheckIcon sx={{ fontSize: 12, color: c('bg-surface') }} /> },
  RETURNED: { ring: 'sla-warn-bar', icon: <ReplayIcon sx={{ fontSize: 11, color: c('sla-warn-bar') }} /> },
  REJECTED: { ring: 'sla-breach-bar', icon: <BlockIcon sx={{ fontSize: 11, color: c('sla-breach-bar') }} /> },
  SUPERSEDED: { ring: 'border-strong' },
  CANCELLED: { ring: 'border-strong' },
  PLANNED: { ring: 'border-strong', dashed: true },
};

function assigneeText(stage: StageView, currentUserId: string | undefined): string | null {
  if (stage.needsReassignment) return t.stageReassign;
  if (stage.assigneeId && stage.assigneeId === currentUserId) return t.stageAssignee(t.stageYou);
  if (stage.assigneeName) return t.stageAssignee(stage.assigneeName);
  if (stage.assigneeId) return t.stageAssigned;
  return stage.status === 'QUEUED' ? t.stageUnassigned : null;
}

/** Подпись статуса: у согласований — решение («Согласовано», «Возвращено», «Отклонено»). */
function stateLabel(type: StageType, state: RowState): string {
  if (state === 'PLANNED') return t.stagePlanned;
  if (APPROVAL_STAGES.has(type) && DECIDED_STATUSES.has(state)) return t.approvalResult[state as 'DONE'];
  return texts.stageStatus[state];
}

interface Row {
  key: string;
  type: StageType;
  state: RowState;
  wave: number;
  assignee: string | null;
  rules: string[];
}

// Маршрут заявки. Основной источник — план GET /requests/{id}/route: этапы по волнам согласований, в том
// числе ещё не созданные. Пока шлюз его не отдаёт или запрос не удался, показываются созданные этапы из
// RequestView.stages. Клиенту — названия и статусы; исполнитель и правила — только оператору.
export function RouteStages({ request, client }: { request: RequestView; client: boolean }) {
  const { user } = useSession();
  const stages = request.stages ?? [];
  const planQuery = useQuery({
    queryKey: queryKeys.route(request.id),
    queryFn: () => requestsApi.route(request.id),
    enabled: !BEFORE_ROUTE.has(request.status),
    retry: false,
  });
  const plan = planQuery.data?.stages;

  const byId = new Map(stages.map((s) => [s.id, s]));
  const rows: Row[] = plan?.length
    ? plan.map((entry: RouteStageView, index) => {
        const created = entry.stageId ? byId.get(entry.stageId) : undefined;
        const state: RowState = created?.status ?? entry.status ?? 'PLANNED';
        return {
          key: entry.stageId ?? `${entry.type}-${index}`,
          type: entry.type,
          state,
          wave: entry.wave,
          assignee: client || !created ? null : assigneeText(created, user?.id),
          rules: client ? [] : entry.requiredByRules,
        };
      })
    : stages.map((s) => ({
        key: s.id,
        type: s.type,
        state: s.status,
        wave: 0,
        assignee: client ? null : assigneeText(s, user?.id),
        rules: client ? [] : (s.requiredByRules ?? []),
      }));

  if (rows.length === 0) {
    return (
      <Typography variant="body1" sx={{ color: c('text-muted') }} data-testid="route-empty">
        {BEFORE_ROUTE.has(request.status) ? t.routeEmpty : t.routeNoStages}
      </Typography>
    );
  }

  return (
    <>
      {rows.map((row, index) => (
        <Stack key={row.key} sx={{ gap: '8px' }}>
          {row.wave > 0 && row.wave !== rows[index - 1]?.wave ? (
            <Typography
              variant="caption"
              sx={{ color: c('text-muted'), textTransform: 'uppercase', letterSpacing: '0.04em', pt: '4px' }}
              data-testid={`route-wave-${row.wave}`}
            >
              {t.routeWave(row.wave)}
            </Typography>
          ) : null}
          <StageRow row={row} />
        </Stack>
      ))}
      {plan?.length || FINAL.has(request.status) ? null : (
        <Typography variant="caption" sx={{ color: c('text-muted') }} data-testid="route-more">
          {t.routeMore}
        </Typography>
      )}
    </>
  );
}

function StageRow({ row }: { row: Row }) {
  const mark = MARK[row.state];
  const active = row.state === 'IN_PROGRESS' || row.state === 'QUEUED';
  return (
    <Box
      sx={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}
      data-testid="route-phase"
      data-state={row.state}
      data-stage={row.type}
      data-wave={row.wave || undefined}
    >
      <Box
        aria-hidden
        sx={{
          width: 18,
          height: 18,
          mt: '2px',
          flexShrink: 0,
          borderRadius: '50%',
          boxSizing: 'border-box',
          border: `2px ${mark.dashed ? 'dashed' : 'solid'} ${c(mark.ring)}`,
          bgcolor: mark.fill ? c(mark.ring) : 'transparent',
          display: 'grid',
          placeItems: 'center',
        }}
      >
        {mark.icon}
        {row.state === 'IN_PROGRESS' ? (
          <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: c('action-primary') }} />
        ) : null}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="subtitle1" sx={{ color: c(active || mark.fill ? 'text-primary' : 'text-secondary') }}>
          {texts.stageType[row.type]}
        </Typography>
        <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
          {stateLabel(row.type, row.state)}
        </Typography>
        {row.assignee ? (
          <Typography
            variant="caption"
            component="div"
            sx={{ color: c('text-muted') }}
            data-testid="route-stage-assignee"
          >
            {row.assignee}
          </Typography>
        ) : null}
        {row.rules.length > 0 ? (
          <Typography variant="caption" component="div" sx={{ color: c('text-muted') }} data-testid="route-stage-rules">
            {t.stageRules(row.rules.join(', '))}
          </Typography>
        ) : null}
      </Box>
    </Box>
  );
}
