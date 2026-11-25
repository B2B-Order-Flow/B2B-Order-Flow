import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { SlaState } from '../../lib/sla';
import { c, type ColorToken } from '../../theme/tokens';

// Компонент «Срок этапа» из Figma (sla.md §10): шкала — доля израсходованного норматива,
// состояние всегда подписано словами, а не только цветом.

const COLORS: Record<SlaState, { bar: ColorToken | null; text: ColorToken }> = {
  ok: { bar: 'sla-ok-bar', text: 'sla-ok-text' },
  warn: { bar: 'sla-warn-bar', text: 'sla-warn-text' },
  breach: { bar: 'sla-breach-bar', text: 'sla-breach-text' },
  paused: { bar: null, text: 'text-muted' },
  stopped: { bar: null, text: 'text-muted' },
};

export function SlaIndicator({
  label,
  value,
  state,
  share,
  testId = 'sla-indicator',
  attributes,
}: {
  label: string;
  value: string;
  state: SlaState;
  /** Доля израсходованного норматива, 0…1. */
  share: number;
  testId?: string;
  /** Дополнительные data-атрибуты корня для автотестов, например вид срока. */
  attributes?: Record<`data-${string}`, string | undefined>;
}) {
  const color = COLORS[state];
  const width = state === 'breach' ? 100 : Math.max(0, Math.min(1, share)) * 100;
  return (
    <Box
      sx={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%' }}
      data-testid={testId}
      {...attributes}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: '12px' }}>
        <Typography variant="body1" component="span" noWrap>
          {label}
        </Typography>
        <Typography
          variant="subtitle2"
          component="span"
          noWrap
          sx={{ color: c(color.text), flexShrink: 0 }}
          data-testid={`${testId}-value`}
          data-state={state}
        >
          {value}
        </Typography>
      </Box>
      <Box
        role="progressbar"
        aria-label={`${label}: ${value}`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(width)}
        sx={{
          height: 5,
          borderRadius: '3px',
          bgcolor: color.bar ? c('sla-track') : c('sla-paused'),
          overflow: 'hidden',
        }}
      >
        {color.bar ? (
          <Box sx={{ width: `${width}%`, height: '100%', borderRadius: '3px', bgcolor: c(color.bar) }} />
        ) : null}
      </Box>
    </Box>
  );
}
