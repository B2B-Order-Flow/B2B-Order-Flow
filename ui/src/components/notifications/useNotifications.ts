import { useMutation, useQueries, useQuery } from '@tanstack/react-query';
import { notificationsApi, queryKeys, requestsApi } from '../../api/endpoints';
import type { NotificationView } from '../../api/types';
import { useNotify } from '../Notifier';

const UNREAD_QUERY = { unread: true, size: 1 };
const REFRESH_MS = 30_000;
const NUMBER_STALE_MS = 5 * 60_000;

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

/** В NotificationView нет номера заявки, поэтому он берётся из карточки. Недоступная заявка — без номера. */
export function useRequestNumbers(items: NotificationView[]): Map<string, string> {
  const ids = [...new Set(items.flatMap((item) => (item.requestId ? [item.requestId] : [])))];
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: queryKeys.request(id),
      queryFn: () => requestsApi.get(id),
      retry: false,
      staleTime: NUMBER_STALE_MS,
    })),
    combine: (results) =>
      new Map(results.flatMap((result) => (result.data ? [[result.data.id, result.data.number] as const] : []))),
  });
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
