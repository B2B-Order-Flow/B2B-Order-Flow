import Chip from '@mui/material/Chip';
import type { RequestStatus } from '../api/types';
import { texts } from '../texts/ru';

// Цвета бейджей статусов — ui-requirements.md §5.1.
const STATUS_COLORS: Record<RequestStatus, { bg: string; fg: string; outlined?: boolean }> = {
  DRAFT: { bg: '#e7e9ee', fg: '#3c4250' },
  DATA_CHECK: { bg: '#dde9fb', fg: '#1d4f91' },
  WITH_MANAGER: { bg: '#dde9fb', fg: '#1d4f91' },
  TECH_CHECK: { bg: '#dde9fb', fg: '#1d4f91' },
  IN_APPROVAL: { bg: '#ece3f7', fg: '#5b2f8f' },
  REWORK: { bg: '#fde7d4', fg: '#97470b' },
  CONTRACT_SIGNING: { bg: '#fdf3c8', fg: '#7a5a00' },
  IN_EXECUTION: { bg: '#dde9fb', fg: '#1d4f91' },
  ACTIVATED: { bg: '#dcf2e3', fg: '#1d6b3a' },
  CANCEL_REQUESTED: { bg: '#fde7d4', fg: '#97470b' },
  CLOSED: { bg: 'transparent', fg: '#1d6b3a', outlined: true },
  REJECTED: { bg: '#fbe0e0', fg: '#9b1c1c' },
  CANCELLED: { bg: '#d9dbe0', fg: '#2f3440' },
};

export function StatusBadge({ status }: { status: RequestStatus }) {
  const color = STATUS_COLORS[status];
  return (
    <Chip
      size="small"
      label={texts.requestStatus[status].label}
      variant={color.outlined ? 'outlined' : 'filled'}
      data-testid="status-badge"
      sx={{
        bgcolor: color.bg,
        color: color.fg,
        borderColor: color.outlined ? color.fg : undefined,
        fontWeight: 600,
      }}
    />
  );
}
