import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { notificationsApi, queryKeys } from '../api/endpoints';
import { NotificationList } from '../components/notifications/NotificationList';
import { useReadAllNotifications, useUnreadCount } from '../components/notifications/useNotifications';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { Muted, Page, PageTitle, Panel } from '../components/ui/Panel';
import { texts } from '../texts/ru';
import { c } from '../theme/tokens';

const t = texts.notifications;
const PAGE_SIZE = 20;

// UI-14. Уведомления (ui-requirements.md §7.4, notifications.md §7).
export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const unread = useUnreadCount();
  const readAll = useReadAllNotifications();
  const listQuery = { page, size: PAGE_SIZE };
  const query = useQuery({
    queryKey: queryKeys.notifications(listQuery),
    queryFn: () => notificationsApi.list(listQuery),
    placeholderData: (previous) => previous,
  });
  const data = query.data;

  return (
    <Page gap={22} top={28} testId="notifications-page">
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
        <PageTitle>{texts.screens.notifications}</PageTitle>
        <Button
          variant="outlined"
          disabled={unread === 0}
          loading={readAll.isPending}
          onClick={() => readAll.mutate()}
          data-testid="action-read-all"
        >
          {t.readAll}
        </Button>
      </Box>
      <Muted>{t.intro}</Muted>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : !data || data.content.length === 0 ? (
        <EmptyState text={t.empty} />
      ) : (
        <>
          <Panel gap={0} px={12} py={12}>
            <NotificationList items={data.content} rowPrefix="notifications" />
          </Panel>
          {data.page.totalPages > 1 ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Typography variant="caption" sx={{ color: c('text-muted') }} data-testid="notifications-page-number">
                {t.pageOf(data.page.number + 1, data.page.totalPages)}
              </Typography>
              <Button variant="text" sx={{ height: 32 }} disabled={page === 0} onClick={() => setPage(page - 1)}>
                {texts.requests.prev}
              </Button>
              <Button
                variant="text"
                sx={{ height: 32 }}
                disabled={page + 1 >= data.page.totalPages}
                onClick={() => setPage(page + 1)}
              >
                {texts.requests.next}
              </Button>
            </Box>
          ) : null}
        </>
      )}
    </Page>
  );
}
