import Box from '@mui/material/Box';
import type { CompanyStatus, RequestStatus } from '../api/types';
import { texts } from '../texts/ru';
import { c, radius, textStyles, type ColorToken } from '../theme/tokens';

// Метки статусов — компонент «Статус» в Figma; тоны по ui-requirements.md §5.1.

export type ChipTone = 'neutral' | 'indigo' | 'purple' | 'orange' | 'ochre' | 'green' | 'closed' | 'red' | 'cancelled';

const TONE_COLORS: Record<ChipTone, { bg: ColorToken | null; fg: ColorToken }> = {
  neutral: { bg: 'status-neutral-bg', fg: 'status-neutral-fg' },
  indigo: { bg: 'status-indigo-bg', fg: 'status-indigo-fg' },
  purple: { bg: 'status-purple-bg', fg: 'status-purple-fg' },
  orange: { bg: 'status-orange-bg', fg: 'status-orange-fg' },
  ochre: { bg: 'status-ochre-bg', fg: 'status-ochre-fg' },
  green: { bg: 'status-green-bg', fg: 'status-green-fg' },
  closed: { bg: null, fg: 'status-green-fg' },
  red: { bg: 'status-red-bg', fg: 'status-red-fg' },
  cancelled: { bg: 'status-cancelled-bg', fg: 'status-cancelled-fg' },
};

export const STATUS_TONES: Record<RequestStatus, ChipTone> = {
  DRAFT: 'neutral',
  DATA_CHECK: 'indigo',
  WITH_MANAGER: 'indigo',
  TECH_CHECK: 'indigo',
  IN_APPROVAL: 'purple',
  REWORK: 'orange',
  CONTRACT_SIGNING: 'ochre',
  IN_EXECUTION: 'indigo',
  ACTIVATED: 'green',
  CANCEL_REQUESTED: 'orange',
  CLOSED: 'closed',
  REJECTED: 'red',
  CANCELLED: 'cancelled',
};

const COMPANY_TONES: Record<CompanyStatus, ChipTone> = { PENDING: 'ochre', ACTIVE: 'green', REJECTED: 'red' };

export function Chip({ tone, label, testId }: { tone: ChipTone; label: string; testId?: string }) {
  const color = TONE_COLORS[tone];
  return (
    <Box
      component="span"
      data-testid={testId}
      sx={{
        ...textStyles.label,
        display: 'inline-flex',
        alignItems: 'center',
        alignSelf: 'flex-start',
        flexShrink: 0,
        whiteSpace: 'nowrap',
        px: '12px',
        py: '4px',
        borderRadius: `${radius.full}px`,
        color: c(color.fg),
        bgcolor: color.bg ? c(color.bg) : 'transparent',
        boxShadow: color.bg ? 'none' : `inset 0 0 0 1px ${c(color.fg)}`,
      }}
    >
      {label}
    </Box>
  );
}

export function StatusBadge({ status }: { status: RequestStatus }) {
  return <Chip tone={STATUS_TONES[status]} label={texts.requestStatus[status].label} testId="status-badge" />;
}

export function CompanyStatusBadge({ status }: { status: CompanyStatus }) {
  return <Chip tone={COMPANY_TONES[status]} label={texts.companyStatus[status]} testId="company-status" />;
}

/** Метка «Ошибка проверки данных» (EC-10) — только оператору. */
export function IntegrationErrorBadge() {
  return <Chip tone="red" label={texts.integrationError.operator} testId="integration-error" />;
}
