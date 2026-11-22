import Box, { type BoxProps } from '@mui/material/Box';
import Stack, { type StackProps } from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { FormEvent, ReactNode } from 'react';
import { c, radius } from '../../theme/tokens';

// Базовые блоки макета: карточка, боковая панель, сетка «подпись — значение», страница.

type PanelTone = 'surface' | 'soft';

/** Карточка: белая (тёмная) поверхность с рамкой, скругление 16. */
export function Panel({
  tone = 'surface',
  gap = 16,
  px = 28,
  py = 24,
  sx,
  children,
  component,
  onSubmit,
  ...rest
}: Omit<StackProps, 'px' | 'py' | 'onSubmit'> & {
  tone?: PanelTone;
  gap?: number;
  px?: number;
  py?: number;
  /** form — карточка-форма: отправка по Enter, без встроенной проверки браузера. */
  component?: 'form';
  noValidate?: boolean;
  onSubmit?: (event: FormEvent) => void;
}) {
  const formProps = component === 'form' ? { component: 'form' as const, noValidate: true, onSubmit } : {};
  return (
    <Stack
      {...rest}
      {...formProps}
      sx={[
        {
          gap: `${gap}px`,
          px: `${px}px`,
          py: `${py}px`,
          borderRadius: `${radius.lg}px`,
          minWidth: 0,
          ...(tone === 'surface'
            ? { bgcolor: c('bg-surface'), border: `1px solid ${c('border-default')}` }
            : { bgcolor: c('action-primary-soft') }),
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {children}
    </Stack>
  );
}

export function PanelTitle({ children }: { children: ReactNode }) {
  return (
    <Typography variant="h2" component="h2">
      {children}
    </Typography>
  );
}

/** Подпись и значение: «ИНН» мелким серым, значение акцентом. */
export function KeyValue({ label, value, testId }: { label: string; value: ReactNode; testId?: string }) {
  return (
    <Stack sx={{ gap: '2px', minWidth: 0 }} data-testid={testId}>
      <Typography variant="caption" sx={{ color: c('text-muted') }}>
        {label}
      </Typography>
      <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/** Поля в строку с переносом, как в макете: отступ между полями 32–40, между рядами 14–18. */
export function KeyValueGrid({
  items,
  columnGap = 32,
  rowGap = 14,
}: {
  items: { label: string; value: ReactNode; testId?: string }[];
  columnGap?: number;
  rowGap?: number;
}) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: `${columnGap}px`, rowGap: `${rowGap}px` }}>
      {items.map((item) => (
        <KeyValue key={item.label} {...item} />
      ))}
    </Box>
  );
}

/** Страница внутри каркаса: вертикальный поток блоков с отступом сверху. */
export function Page({
  gap = 20,
  top = 36,
  children,
  testId,
}: {
  gap?: number;
  top?: number;
  children: ReactNode;
  testId?: string;
}) {
  return (
    <Stack sx={{ gap: `${gap}px`, pt: `${top}px`, pb: '48px' }} data-testid={testId}>
      {children}
    </Stack>
  );
}

/** Две колонки: основная растягивается, боковая — фиксированной ширины или тоже растягивается. */
export function Columns({ children, sx, ...rest }: BoxProps) {
  return (
    <Box
      {...rest}
      sx={[{ display: 'flex', gap: '24px', alignItems: 'flex-start' }, ...(Array.isArray(sx) ? sx : [sx])]}
    >
      {children}
    </Box>
  );
}

export function PageTitle({ children, testId }: { children: ReactNode; testId?: string }) {
  return (
    <Typography variant="h1" component="h1" data-testid={testId}>
      {children}
    </Typography>
  );
}

export function Muted({ children, variant = 'body1' }: { children: ReactNode; variant?: 'body1' | 'caption' }) {
  return (
    <Typography variant={variant} sx={{ color: c('text-muted') }}>
      {children}
    </Typography>
  );
}
