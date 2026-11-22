import BlockIcon from '@mui/icons-material/Block';
import CheckIcon from '@mui/icons-material/Check';
import ReplayIcon from '@mui/icons-material/Replay';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQueries } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { queryKeys, usersApi } from '../../api/endpoints';
import type { RequestStatus, RequestView, Role, StageStatus, StageView } from '../../api/types';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, type ColorToken } from '../../theme/tokens';

const t = texts.requestCard;

const BEFORE_ROUTE: ReadonlySet<RequestStatus> = new Set(['DRAFT', 'DATA_CHECK', 'WITH_MANAGER']);
const FINAL: ReadonlySet<RequestStatus> = new Set(['CLOSED', 'REJECTED', 'CANCELLED']);

const MARK: Record<StageStatus, { ring: ColorToken; fill?: boolean; icon?: ReactNode }> = {
  QUEUED: { ring: 'border-strong' },
  IN_PROGRESS: { ring: 'action-primary' },
  DONE: { ring: 'sla-ok-bar', fill: true, icon: <CheckIcon sx={{ fontSize: 12, color: c('bg-surface') }} /> },
  RETURNED: { ring: 'sla-warn-bar', icon: <ReplayIcon sx={{ fontSize: 11, color: c('sla-warn-bar') }} /> },
  REJECTED: { ring: 'sla-breach-bar', icon: <BlockIcon sx={{ fontSize: 11, color: c('sla-breach-bar') }} /> },
  SUPERSEDED: { ring: 'border-strong' },
  CANCELLED: { ring: 'border-strong' },
};

/** Имена исполнителей: список сотрудников доступен только координатору (api-contract.md §6.1). */
function useAssigneeNames(stages: StageView[], enabled: boolean): Map<string, string> {
  const roles = enabled
    ? [...new Set(stages.flatMap((s) => (s.assigneeId && s.queueRole ? [s.queueRole] : [])))]
    : ([] as Role[]);
  return useQueries({
    queries: roles.map((role) => ({
      queryKey: queryKeys.usersByRole(role),
      queryFn: () => usersApi.byRole(role),
      retry: false,
    })),
    combine: (results) =>
      new Map(results.flatMap((result) => (result.data?.content ?? []).map((u) => [u.id, u.fullName] as const))),
  });
}

function assigneeText(stage: StageView, currentUserId: string | undefined, names: Map<string, string>): string | null {
  if (stage.needsReassignment) return t.stageReassign;
  if (stage.assigneeId) {
    const name = stage.assigneeId === currentUserId ? t.stageYou : (names.get(stage.assigneeId) ?? t.stageAssigned);
    return t.stageAssignee(name);
  }
  return stage.status === 'QUEUED' ? t.stageUnassigned : null;
}

// Созданные этапы текущей версии маршрута в порядке ядра. План будущих этапов — неделя 3 (GET /requests/{id}/route).
// Клиенту — названия и статусы; исполнитель и правила — только оператору, SLA — в отдельном блоке.
export function RouteStages({ request, client }: { request: RequestView; client: boolean }) {
  const { user, activeRole } = useSession();
  const stages = request.stages ?? [];
  const names = useAssigneeNames(stages, activeRole === 'COORDINATOR');

  if (stages.length === 0) {
    return (
      <Typography variant="body1" sx={{ color: c('text-muted') }} data-testid="route-empty">
        {BEFORE_ROUTE.has(request.status) ? t.routeEmpty : t.routeNoStages}
      </Typography>
    );
  }

  return (
    <>
      {stages.map((stage) => {
        const assignee = client ? null : assigneeText(stage, user?.id, names);
        const rules = client ? [] : (stage.requiredByRules ?? []);
        const mark = MARK[stage.status];
        const active = stage.status === 'IN_PROGRESS' || stage.status === 'QUEUED';
        return (
          <Box
            key={stage.id}
            sx={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}
            data-testid="route-phase"
            data-state={stage.status}
            data-stage={stage.type}
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
                border: `2px solid ${c(mark.ring)}`,
                bgcolor: mark.fill ? c(mark.ring) : 'transparent',
                display: 'grid',
                placeItems: 'center',
              }}
            >
              {mark.icon}
              {stage.status === 'IN_PROGRESS' ? (
                <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: c('action-primary') }} />
              ) : null}
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography
                variant="subtitle1"
                sx={{ color: c(active || mark.fill ? 'text-primary' : 'text-secondary') }}
              >
                {texts.stageType[stage.type]}
              </Typography>
              <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
                {texts.stageStatus[stage.status]}
              </Typography>
              {assignee ? (
                <Typography
                  variant="caption"
                  component="div"
                  sx={{ color: c('text-muted') }}
                  data-testid="route-stage-assignee"
                >
                  {assignee}
                </Typography>
              ) : null}
              {rules.length > 0 ? (
                <Typography
                  variant="caption"
                  component="div"
                  sx={{ color: c('text-muted') }}
                  data-testid="route-stage-rules"
                >
                  {t.stageRules(rules.join(', '))}
                </Typography>
              ) : null}
            </Box>
          </Box>
        );
      })}
      {FINAL.has(request.status) ? null : (
        <Stack>
          <Typography variant="caption" sx={{ color: c('text-muted') }} data-testid="route-more">
            {t.routeMore}
          </Typography>
        </Stack>
      )}
    </>
  );
}
