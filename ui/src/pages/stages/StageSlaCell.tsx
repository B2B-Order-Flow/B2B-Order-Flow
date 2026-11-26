import Typography from '@mui/material/Typography';
import type { StageSlaSummary, StageView } from '../../api/types';
import { formatDuration, isCalendarTimer } from '../../lib/sla';
import { texts } from '../../texts/ru';
import { c, type ColorToken } from '../../theme/tokens';

const t = texts.queue.sla;

type CellState = 'BREACHED' | 'RUNNING' | 'PAUSED' | 'STOPPED';

/** Норматив нарушен: по признаку, по статусу или по отрицательному остатку. remainingMinutes необязателен. */
const isOverdue = (sla: StageSlaSummary) =>
  Boolean(sla.overdue) || sla.status === 'BREACHED' || (sla.remainingMinutes ?? 0) < 0;

/**
 * Одно правило для текста, data-state и сортировки: «завершён» и «на паузе» важнее просрочки,
 * просрочка важнее «идёт». Сама просрочка у завершённого и приостановленного этапа видна в тексте
 * и в атрибуте data-overdue.
 */
function cellState(sla: StageSlaSummary): CellState {
  if (sla.status === 'STOPPED') return 'STOPPED';
  if (sla.status === 'PAUSED') return 'PAUSED';
  return isOverdue(sla) ? 'BREACHED' : 'RUNNING';
}

/**
 * Ячейка «Срок этапа» в очередях и «Моих задачах» по краткой сводке StageView.sla (решение Димы и
 * Кирилла недели 3). Календарь — как в карточке: стройка в календарном времени (sla.md §4), остальные
 * этапы в рабочем. Полный срок с журналом пауз — в карточке заявки.
 */
export function StageSlaCell({ stage }: { stage: StageView }) {
  const sla = stage.sla;
  if (!sla) {
    return (
      <Typography
        variant="body1"
        component="span"
        sx={{ color: c('text-muted') }}
        title={texts.queue.slaLater}
        data-testid="stage-sla-cell"
      >
        —
      </Typography>
    );
  }

  const work = !isCalendarTimer({ kind: 'STAGE_SLA', stageType: stage.type });
  const minutes = sla.remainingMinutes;
  const overdue = isOverdue(sla);
  const state = cellState(sla);
  let text: string;
  let color: ColorToken = 'text-muted';
  if (state === 'STOPPED') text = overdue ? t.stoppedOverdue : t.stopped;
  else if (state === 'PAUSED') text = overdue ? t.pausedOverdue : t.paused;
  else if (state === 'BREACHED') {
    text = minutes !== undefined && minutes < 0 ? t.overdueBy(formatDuration(-minutes, work)) : t.overdue;
    color = 'sla-breach-text';
  } else {
    text = minutes !== undefined ? t.left(formatDuration(minutes, work)) : t.running;
    color = 'text-primary';
  }

  return (
    <Typography
      variant="body1"
      component="span"
      sx={{ color: c(color) }}
      data-testid="stage-sla-cell"
      data-state={state}
      data-overdue={overdue ? 'true' : undefined}
    >
      {text}
    </Typography>
  );
}

const RANK: Record<CellState, number> = { BREACHED: 0, RUNNING: 1, PAUSED: 2, STOPPED: 3 };

/**
 * Порядок очереди: сначала просроченные (по величине просрочки, без остатка — как только что
 * нарушенные), затем идущие по остатку, затем приостановленные и завершённые, без сводки — в конце.
 * При равенстве выше этап, который дольше ждёт.
 */
export function byUrgency(a: StageView, b: StageView): number {
  const rank = (s: StageView) => (s.sla ? RANK[cellState(s.sla)] : 4);
  const left = (s: StageView) => {
    if (!s.sla) return Number.POSITIVE_INFINITY;
    const minutes = s.sla.remainingMinutes;
    if (cellState(s.sla) === 'BREACHED') return Math.min(minutes ?? 0, 0);
    return minutes ?? Number.POSITIVE_INFINITY;
  };
  return rank(a) - rank(b) || Math.sign(left(a) - left(b)) || Date.parse(a.createdAt) - Date.parse(b.createdAt);
}
