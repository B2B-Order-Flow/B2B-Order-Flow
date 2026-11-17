import Typography from '@mui/material/Typography';
import type { StageView } from '../../api/types';
import { SlaIndicator } from '../../components/ui/SlaIndicator';
import { slaSnapshot } from '../../lib/sla';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';

/** Ячейка «Срок этапа» в очередях: норматив, остаток и шкала. */
export function StageSlaCell({ stage }: { stage: StageView }) {
  if (!stage.sla) {
    return (
      <Typography variant="caption" sx={{ color: c('text-muted') }}>
        {texts.requestCard.slaUnknown}
      </Typography>
    );
  }
  const snapshot = slaSnapshot(stage.sla);
  return (
    <SlaIndicator
      label={snapshot.normLabel}
      value={snapshot.valueLabel}
      state={snapshot.state}
      share={snapshot.share}
    />
  );
}

/** Остаток SLA в минутах для сортировки очереди: меньше — выше (sla.md §10). Без таймера — в конец. */
export function remainingMinutes(stage: StageView): number {
  if (!stage.sla) return Number.POSITIVE_INFINITY;
  return stage.sla.normMinutes - stage.sla.elapsedSlaSeconds / 60;
}

export const byRemainingSla = (a: StageView, b: StageView) => remainingMinutes(a) - remainingMinutes(b);
