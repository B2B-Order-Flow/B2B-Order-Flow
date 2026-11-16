import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { KeyboardEvent, ReactNode } from 'react';
import { c, radius, textStyles } from '../../theme/tokens';

// Таблица из макетов: карточка, строка заголовка мелким серым, строки с линией сверху.
// Колонки с фиксированной шириной (как в Figma), одна колонка без ширины растягивается.

export interface Column<T> {
  key: string;
  title: string;
  /** Ширина содержимого колонки, px; без ширины — колонка занимает остаток. */
  width?: number;
  render: (row: T) => ReactNode;
}

const SIDE = 28;

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  gap = 16,
  rowPaddingY = 14,
  testId,
  rowTestId,
  label,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  gap?: number;
  rowPaddingY?: number;
  testId?: string;
  rowTestId?: string;
  /** Подпись таблицы для экранного диктора. */
  label?: string;
}) {
  const last = columns.length - 1;
  const cellPadding = (index: number, paddingY: number) => ({
    pt: `${paddingY}px`,
    pb: `${paddingY}px`,
    pl: `${index === 0 ? SIDE : gap}px`,
    pr: index === last ? `${SIDE}px` : 0,
  });

  const onKeyDown = (event: KeyboardEvent, row: T) => {
    if (onRowClick && (event.key === 'Enter' || event.key === ' ') && event.target === event.currentTarget) {
      event.preventDefault();
      onRowClick(row);
    }
  };

  return (
    <Box
      sx={{
        bgcolor: c('bg-surface'),
        border: `1px solid ${c('border-default')}`,
        borderRadius: `${radius.lg}px`,
        py: '8px',
        overflow: 'hidden',
      }}
      data-testid={testId}
    >
      <Box
        component="table"
        aria-label={label}
        sx={{ width: '100%', tableLayout: 'fixed', borderCollapse: 'collapse' }}
      >
        <colgroup>
          {columns.map((column, index) => (
            <col
              key={column.key}
              style={
                column.width
                  ? { width: column.width + (index === 0 ? SIDE : gap) + (index === last ? SIDE : 0) }
                  : undefined
              }
            />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((column, index) => (
              <Box
                component="th"
                key={column.key}
                scope="col"
                sx={{ ...cellPadding(index, 12), ...textStyles.caption, color: c('text-muted'), textAlign: 'left' }}
              >
                {column.title}
              </Box>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <Box
              component="tr"
              key={rowKey(row)}
              data-testid={rowTestId}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={onRowClick ? (event: KeyboardEvent) => onKeyDown(event, row) : undefined}
              tabIndex={onRowClick ? 0 : undefined}
              sx={{
                borderTop: `1px solid ${c('border-default')}`,
                cursor: onRowClick ? 'pointer' : 'default',
                '&:hover': onRowClick ? { bgcolor: c('bg-surface-2') } : undefined,
                '&:focus-visible': { outline: `2px solid ${c('action-primary')}`, outlineOffset: -2 },
              }}
            >
              {columns.map((column, index) => (
                <Box
                  component="td"
                  key={column.key}
                  sx={{ ...cellPadding(index, rowPaddingY), verticalAlign: 'middle', overflowWrap: 'anywhere' }}
                >
                  {column.render(row)}
                </Box>
              ))}
            </Box>
          ))}
        </tbody>
      </Box>
    </Box>
  );
}

/** Текст ячейки: первая колонка — акцент, остальные — основной текст. */
export function Cell({ children, strong }: { children: ReactNode; strong?: boolean }) {
  return (
    <Typography variant={strong ? 'subtitle1' : 'body1'} component="div">
      {children}
    </Typography>
  );
}

/** Ячейка в два ряда: «Компания» акцентом и адрес подписью. */
export function TwoLineCell({ title, subtitle }: { title: ReactNode; subtitle?: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <Typography variant="subtitle1" component="div">
        {title}
      </Typography>
      {subtitle ? (
        <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
          {subtitle}
        </Typography>
      ) : null}
    </Box>
  );
}
