import Typography from '@mui/material/Typography';
import type { StageView } from '../../api/types';
import { formatDuration, isCalendarTimer } from '../../lib/sla';
import { texts } from '../../texts/ru';
import { c, type ColorToken } from '../../theme/tokens';

const t = texts.queue.sla;

/**
 * Ячейка «Срок этапа» в очередях и «Моих задачах» по краткой сводке StageView.sla (решение Димы и
 * Кирилла недели 3). Календарь — как в карточке: стройка в календарном времени (sla.md §4), остальные
 * этапы в рабочем. Полный срок с журналом пауз — в карточке заявки.
 */
export function StageSlaCell({ stage }: { stage: StageView }) {
  const sla = stage.sla;
  let text = '—';
  let color: ColorToken = 'text-muted';
  const work = !isCalendarTimer({ kind: 'STAGE_SLA', stageType: stage.type });
  if (sla) {
    const minutes = sla.remainingMinutes;
    if (sla.status === 'STOPPED') text = t.stopped;
    else if (sla.status === 'PAUSED') text = t.paused;
    else if (sla.overdue || sla.status === 'BREACHED') {
      text = minutes !== undefined && minutes < 0 ? t.overdueBy(formatDuration(-minutes, work)) : t.overdue;
      color = 'sla-breach-text';
    } else if (minutes !== undefined) {
      text = t.left(formatDuration(minutes, work));
      color = 'text-primary';
    }
  }
  return (
    <Typography
      variant="body1"
      component="span"
      sx={{ color: c(color) }}
      title={sla ? undefined : texts.queue.slaLater}
      data-testid="stage-sla-cell"
      data-state={sla?.overdue ? 'BREACHED' : sla?.status}
    >
      {text}
    </Typography>
  );
}

/** Сверху — этапы с меньшим остатком срока (просроченные первыми), без сводки — дольше ждущие. */
export function byUrgency(a: StageView, b: StageView): number {
  const left = (s: StageView) =>
    s.sla && s.sla.status !== 'STOPPED' && s.sla.status !== 'PAUSED' && s.sla.remainingMinutes !== undefined
      ? s.sla.remainingMinutes
      : Number.POSITIVE_INFINITY;
  const diff = left(a) - left(b);
  return diff !== 0 && !Number.isNaN(diff) ? diff : Date.parse(a.createdAt) - Date.parse(b.createdAt);
}
