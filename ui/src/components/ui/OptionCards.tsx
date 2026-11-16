import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useId, type ReactNode } from 'react';
import { c, radius } from '../../theme/tokens';

// Варианты с переключателем (тип тарифа, результат ТВ): карточка с кругом-переключателем,
// названием и подсказкой. Внутри — настоящие radio, поэтому работает стрелками и Tab.

export interface Option<V extends string> {
  value: V;
  label: string;
  hint?: ReactNode;
}

export function OptionCards<V extends string>({
  name,
  label,
  options,
  value,
  onChange,
  direction = 'column',
  testId,
}: {
  name: string;
  /** Подпись группы для экранного диктора. */
  label: string;
  options: Option<V>[];
  value: V | null;
  onChange: (value: V) => void;
  direction?: 'row' | 'column';
  testId?: string;
}) {
  const groupId = useId();
  return (
    <Box
      role="radiogroup"
      aria-label={label}
      data-testid={testId}
      sx={{ display: 'flex', flexDirection: direction, gap: direction === 'row' ? '12px' : '14px' }}
    >
      {options.map((option) => {
        const selected = option.value === value;
        const id = `${groupId}-${option.value}`;
        return (
          <Box
            component="label"
            htmlFor={id}
            key={option.value}
            data-testid={testId ? `${testId}-${option.value}` : undefined}
            sx={{
              flex: 1,
              display: 'flex',
              gap: '12px',
              alignItems: 'flex-start',
              px: '16px',
              py: '14px',
              borderRadius: `${radius.md}px`,
              cursor: 'pointer',
              bgcolor: selected ? c('action-primary-soft') : 'transparent',
              boxShadow: `inset 0 0 0 ${selected ? 2 : 1}px ${selected ? c('action-primary') : c('border-strong')}`,
              '&:has(input:focus-visible)': { outline: `2px solid ${c('action-primary')}`, outlineOffset: 2 },
            }}
          >
            <Box
              component="input"
              type="radio"
              id={id}
              name={name}
              value={option.value}
              checked={selected}
              onChange={() => onChange(option.value)}
              sx={{ position: 'absolute', opacity: 0, width: 0, height: 0 }}
            />
            <Box
              aria-hidden
              sx={{
                width: 20,
                height: 20,
                flexShrink: 0,
                mt: '2px',
                borderRadius: '50%',
                bgcolor: c('bg-surface'),
                border: `2px solid ${selected ? c('action-primary') : c('border-strong')}`,
                display: 'grid',
                placeItems: 'center',
              }}
            >
              {selected ? (
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: c('action-primary') }} />
              ) : null}
            </Box>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px', minWidth: 0 }}>
              <Typography variant="subtitle1" component="span">
                {option.label}
              </Typography>
              {option.hint ? (
                <Typography variant="caption" component="span" sx={{ color: c('text-muted') }}>
                  {option.hint}
                </Typography>
              ) : null}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
