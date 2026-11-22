import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { RequestView, SlaPauseView, SlaTimerView } from '../../api/types';
import { Notice } from '../../components/ui/Notice';
import { Panel, PanelTitle } from '../../components/ui/Panel';
import { SlaIndicator } from '../../components/ui/SlaIndicator';
import { slaSnapshot } from '../../lib/sla';
import { isCompanyRole } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, radius } from '../../theme/tokens';
import { dateAt } from './labels';

const t = texts.sla;
const REFRESH_MS = 30_000;

function timerName(timer: SlaTimerView): string {
  if (timer.kind !== 'STAGE_SLA') return t.kinds[timer.kind];
  return timer.stageType ? texts.stageType[timer.stageType] : t.stageFallback;
}

/** Идущие сроки выше завершённых; внутри групп — порядок ядра. */
const activeFirst = (timers: SlaTimerView[]) => [
  ...timers.filter((timer) => timer.status !== 'STOPPED'),
  ...timers.filter((timer) => timer.status === 'STOPPED'),
];

/** Один срок: индикатор, время по SLA и календарное, текущая пауза (sla.md §10). */
export function SlaTimerRow({ timer, testId }: { timer: SlaTimerView; testId?: string }) {
  const snapshot = slaSnapshot(timer);
  const times = [
    snapshot.slaTimeLabel ? texts.requestCard.slaTime(snapshot.slaTimeLabel) : null,
    snapshot.calendarTimeLabel ? texts.requestCard.calendarTime(snapshot.calendarTimeLabel) : null,
    timer.pause ? texts.requestCard.pausedNow(texts.pauseReason[timer.pause.reasonCode]) : null,
  ].filter(Boolean);
  return (
    <Stack sx={{ gap: '6px', minWidth: 0 }}>
      <SlaIndicator
        label={`${timerName(timer)}, ${snapshot.normLabel}`}
        value={snapshot.valueLabel}
        state={snapshot.state}
        share={snapshot.share}
        testId={testId}
      />
      {times.length ? (
        <Typography variant="caption" sx={{ color: c('text-muted') }}>
          {times.join(' ')}
        </Typography>
      ) : null}
    </Stack>
  );
}

function TimerGroup({ title, timers }: { title: string; timers: SlaTimerView[] }) {
  if (timers.length === 0) return null;
  return (
    <Stack sx={{ gap: '12px' }}>
      <Typography variant="subtitle1" component="h3">
        {title}
      </Typography>
      <Box
        sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', columnGap: '32px', rowGap: '16px' }}
      >
        {activeFirst(timers).map((timer) => (
          <SlaTimerRow
            key={timer.id}
            timer={timer}
            testId={`request-card-sla-${timer.kind === 'STAGE_SLA' ? (timer.stageType ?? timer.id) : timer.kind}`}
          />
        ))}
      </Box>
    </Stack>
  );
}

function PauseJournal({ pauses }: { pauses: SlaPauseView[] }) {
  if (pauses.length === 0) return null;
  return (
    <Stack sx={{ gap: '10px' }} data-testid="request-card-sla-pauses">
      <Typography variant="subtitle1" component="h3">
        {t.pauses}
      </Typography>
      <Stack sx={{ gap: '8px', px: '16px', py: '12px', bgcolor: c('bg-surface-2'), borderRadius: `${radius.md}px` }}>
        {pauses.map((pause) => {
          const period = pause.endedAt
            ? t.pausePeriod(dateAt(pause.startedAt, false), dateAt(pause.endedAt, false))
            : t.pauseOngoing(dateAt(pause.startedAt, false));
          const details = [
            period,
            !pause.endedAt && pause.limitAt ? t.pauseLimit(dateAt(pause.limitAt, false)) : null,
            pause.extendedCount ? t.pauseExtended(pause.extendedCount) : null,
            pause.comment,
          ].filter(Boolean);
          return (
            <Box key={pause.id} data-testid="sla-pause">
              <Typography variant="subtitle2" component="div">
                {texts.pauseReason[pause.reasonCode]}
              </Typography>
              <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
                {details.join(' · ')}
              </Typography>
            </Box>
          );
        })}
      </Stack>
    </Stack>
  );
}

// Блок «Сроки» для ролей оператора (sla.md §10, ui-requirements.md §6.8). Какие таймеры и паузы видит
// роль, решает ядро. Клиенту блок не показывается и запрос не отправляется.
export function RequestSlaPanel({ request }: { request: RequestView }) {
  const { activeRole } = useSession();
  const allowed = activeRole !== null && activeRole !== 'ADMIN' && !isCompanyRole(activeRole);
  const query = useQuery({
    queryKey: queryKeys.sla(request.id),
    queryFn: () => requestsApi.sla(request.id),
    enabled: allowed,
    retry: false,
    refetchInterval: REFRESH_MS,
  });

  if (!allowed || query.isPending) return null;
  if (query.isError) {
    return (
      <Notice
        tone="warning"
        testId="request-card-sla-error"
        action={
          <Button variant="outlined" sx={{ height: 36 }} onClick={() => void query.refetch()}>
            {texts.common.refresh}
          </Button>
        }
      >
        {t.loadError}
      </Notice>
    );
  }

  const { timers, pauses } = query.data;
  if (timers.length === 0 && pauses.length === 0) return null;
  return (
    <Panel gap={18} data-testid="request-card-sla">
      <PanelTitle>{t.title}</PanelTitle>
      <TimerGroup title={t.stages} timers={timers.filter((timer) => timer.kind === 'STAGE_SLA')} />
      <TimerGroup title={t.control} timers={timers.filter((timer) => timer.kind !== 'STAGE_SLA')} />
      <PauseJournal pauses={pauses} />
    </Panel>
  );
}
