import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { notificationsApi, queryKeys } from '../../api/endpoints';
import { canReadAll } from '../../lib/notifications';
import { canAccess, routeAccess } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, radius } from '../../theme/tokens';
import { NotificationList } from '../notifications/NotificationList';
import { useReadAllNotifications, useUnreadCount } from '../notifications/useNotifications';
import { EmptyState, ErrorState, LoadingState } from '../states';

const t = texts.notifications;
const LATEST_QUERY = { size: 5 };

// Колокольчик в шапке: счётчик непрочитанных и последние уведомления (ui-requirements.md §3.1, §7.4).
export function NotificationBell() {
  const { activeRole } = useSession();
  const allowed = canAccess(routeAccess.notifications, activeRole);
  const count = useUnreadCount(allowed);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = () => setAnchor(null);

  if (!allowed) return null;
  return (
    <>
      <IconButton
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-label={t.bell(count)}
        aria-haspopup="dialog"
        data-testid="notifications-bell"
        sx={{ position: 'relative', flexShrink: 0, color: c('text-primary') }}
      >
        <NotificationsNoneIcon />
        {count > 0 ? (
          <Box
            component="span"
            data-testid="notifications-badge"
            sx={{
              position: 'absolute',
              top: 2,
              right: 0,
              minWidth: 18,
              height: 18,
              px: '4px',
              boxSizing: 'border-box',
              borderRadius: `${radius.full}px`,
              bgcolor: c('action-primary'),
              color: c('bg-surface'),
              fontSize: 11,
              fontWeight: 600,
              lineHeight: '18px',
              textAlign: 'center',
            }}
          >
            {count > 99 ? '99+' : count}
          </Box>
        ) : null}
      </IconButton>
      <Popover
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { width: 420, maxWidth: 'calc(100vw - 32px)', mt: '6px' } } }}
      >
        <LatestNotifications count={count} onClose={close} />
      </Popover>
    </>
  );
}

function LatestNotifications({ count, onClose }: { count: number; onClose: () => void }) {
  const readAll = useReadAllNotifications();
  const query = useQuery({
    queryKey: queryKeys.notifications(LATEST_QUERY),
    queryFn: () => notificationsApi.list(LATEST_QUERY),
    retry: false,
  });

  return (
    <Stack sx={{ p: '16px', gap: '10px' }} data-testid="notifications-popover">
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
        <Typography variant="subtitle1" component="h2">
          {texts.screens.notifications}
        </Typography>
        {canReadAll(count, query.data?.content) ? (
          <Button
            variant="text"
            sx={{ height: 32, px: '8px' }}
            loading={readAll.isPending}
            onClick={() => readAll.mutate()}
            data-testid="action-read-all"
          >
            {t.readAll}
          </Button>
        ) : null}
      </Box>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState text={t.empty} />
      ) : (
        <NotificationList items={query.data.content} rowPrefix="notifications-menu" onOpen={onClose} />
      )}
      <Button
        component={RouterLink}
        to="/notifications"
        variant="outlined"
        onClick={onClose}
        data-testid="notifications-all"
      >
        {t.all}
      </Button>
    </Stack>
  );
}
