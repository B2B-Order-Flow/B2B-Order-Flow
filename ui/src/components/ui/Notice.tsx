import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { c, radius, type ColorToken } from '../../theme/tokens';

// Плашки из макетов: ошибка ответа (UI-03), ошибка интеграции (UI-08), ожидание клиента.

type NoticeTone = 'error' | 'warning' | 'waiting' | 'info';

const TONES: Record<NoticeTone, { bg: ColorToken; fg: ColorToken }> = {
  error: { bg: 'status-red-bg', fg: 'status-red-fg' },
  warning: { bg: 'status-orange-bg', fg: 'status-orange-fg' },
  waiting: { bg: 'status-ochre-bg', fg: 'status-ochre-fg' },
  info: { bg: 'status-indigo-bg', fg: 'status-indigo-fg' },
};

export function Notice({
  tone,
  title,
  children,
  action,
  testId,
  size = 'md',
}: {
  tone: NoticeTone;
  title?: ReactNode;
  children?: ReactNode;
  action?: ReactNode;
  testId?: string;
  /** md — плашка в форме (12/16), lg — баннер на странице (16/20). */
  size?: 'md' | 'lg';
}) {
  const color = TONES[tone];
  return (
    <Box
      role={tone === 'error' ? 'alert' : 'status'}
      data-testid={testId}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: '14px',
        px: size === 'lg' ? '20px' : '16px',
        py: size === 'lg' ? '16px' : '12px',
        borderRadius: `${radius.md}px`,
        bgcolor: c(color.bg),
        color: c(color.fg),
      }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, minWidth: 0 }}>
        {title ? (
          <Typography variant="subtitle1" component="div" sx={{ color: 'inherit' }}>
            {title}
          </Typography>
        ) : null}
        {children ? (
          <Typography variant="body1" component="div" sx={{ color: 'inherit' }}>
            {children}
          </Typography>
        ) : null}
      </Box>
      {action}
    </Box>
  );
}
