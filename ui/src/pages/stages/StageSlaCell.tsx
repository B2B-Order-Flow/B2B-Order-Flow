import Typography from '@mui/material/Typography';
import type { StageView } from '../../api/types';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';

/**
 * Ячейка «Срок этапа» в очередях. Сводки SLA в StageView пока нет: отдаст ли её ядро, решат
 * Дима и Кирилл до 24.11. До тех пор показываем «—», а срок этапа — в карточке заявки.
 */
export function StageSlaCell() {
  return (
    <Typography variant="body1" component="span" sx={{ color: c('text-muted') }} title={texts.queue.slaLater}>
      —
    </Typography>
  );
}

/** Без сводки SLA очередь сортируется по времени постановки: дольше ждущие этапы — выше. */
export const byQueuedAt = (a: StageView, b: StageView) => Date.parse(a.createdAt) - Date.parse(b.createdAt);
