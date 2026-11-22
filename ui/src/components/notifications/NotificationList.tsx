import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router';
import type { NotificationView } from '../../api/types';
import { formatShortDateTime } from '../../lib/format';
import { notificationTarget, notificationText } from '../../lib/notifications';
import { isCompanyRole } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, radius } from '../../theme/tokens';
import { useReadNotification, useRequestNumbers } from './useNotifications';

const t = texts.notifications;

// Строка: текст, время, непрочитанные выделены. Клик открывает заявку и отмечает прочитанным (ui-requirements.md §7.4).
export function NotificationList({
  items,
  rowPrefix,
  onOpen,
}: {
  items: NotificationView[];
  rowPrefix: string;
  onOpen?: () => void;
}) {
  const { activeRole } = useSession();
  const navigate = useNavigate();
  const read = useReadNotification();
  const numbers = useRequestNumbers(items);
  const reader = { client: isCompanyRole(activeRole), coordinator: activeRole === 'COORDINATOR' };

  const open = (item: NotificationView, target: string) => {
    if (!item.readAt) read.mutate(item.id);
    onOpen?.();
    void navigate(target);
  };

  return (
    <Stack sx={{ gap: '4px' }}>
      {items.map((item) => {
        const unread = !item.readAt;
        const target = notificationTarget(item);
        const number = item.requestId ? numbers.get(item.requestId) : undefined;
        const content = (
          <Box sx={{ display: 'flex', gap: '10px', minWidth: 0, flex: 1, textAlign: 'left' }}>
            <Box
              aria-hidden
              sx={{
                width: 8,
                height: 8,
                mt: '6px',
                flexShrink: 0,
                borderRadius: '50%',
                bgcolor: unread ? c('action-primary') : 'transparent',
              }}
            />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant={unread ? 'subtitle2' : 'body1'} component="div">
                {notificationText(item, { ...reader, requestNumber: number })}
              </Typography>
              <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
                {formatShortDateTime(item.createdAt)}
                {unread ? ` · ${t.unread}` : ''}
              </Typography>
            </Box>
          </Box>
        );
        return (
          <Box
            key={item.id}
            data-testid={`${rowPrefix}-row-${item.id}`}
            data-state={unread ? 'unread' : 'read'}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              px: '12px',
              py: '10px',
              borderRadius: `${radius.md}px`,
              bgcolor: unread ? c('action-primary-soft') : 'transparent',
            }}
          >
            {target ? (
              <ButtonBase
                onClick={() => open(item, target)}
                sx={{ flex: 1, minWidth: 0, justifyContent: 'flex-start', borderRadius: `${radius.sm}px` }}
              >
                {content}
              </ButtonBase>
            ) : (
              content
            )}
            {unread ? (
              <Button
                variant="text"
                sx={{ height: 32, px: '8px', flexShrink: 0 }}
                disabled={read.isPending && read.variables === item.id}
                onClick={() => read.mutate(item.id)}
                data-testid="action-read"
              >
                {t.read}
              </Button>
            ) : null}
          </Box>
        );
      })}
    </Stack>
  );
}
