import { useMutation, useQuery } from '@tanstack/react-query';
import { notificationsApi, queryKeys } from '../../api/endpoints';
import { useNotify } from '../Notifier';

const UNREAD_QUERY = { unread: true, size: 1 };
const REFRESH_MS = 30_000;

/** Счётчик непрочитанных: до WebSocket — раз в 30 секунд и после действий (ui-requirements.md §7.4). */
export function useUnreadCount(enabled = true): number {
  const query = useQuery({
    queryKey: queryKeys.notifications(UNREAD_QUERY),
    queryFn: () => notificationsApi.list(UNREAD_QUERY),
    enabled,
    retry: false,
    refetchInterval: REFRESH_MS,
  });
  return query.data?.page.totalElements ?? 0;
}

// Счётчик и списки обновляет общий обработчик мутаций в createQueryClient.
export function useReadNotification() {
  const notify = useNotify();
  return useMutation({ mutationFn: notificationsApi.read, onError: notify.error });
}

export function useReadAllNotifications() {
  const notify = useNotify();
  return useMutation({ mutationFn: notificationsApi.readAll, onError: notify.error });
}
