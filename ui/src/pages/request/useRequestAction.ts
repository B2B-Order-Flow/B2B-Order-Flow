import { useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../api/endpoints';
import { isApiError } from '../../api/errors';
import type { RequestView } from '../../api/types';
import { useNotify } from '../../components/Notifier';

/**
 * Действие над заявкой: после успеха кладёт ответ в кэш и показывает тост; при конфликте версии
 * или сменившемся статусе перечитывает заявку (api-contract.md §5.2–5.3, ui-requirements.md §10).
 * options.onError возвращает true, если экран сам показал понятный текст, — тогда общий тост не нужен.
 */
export function useRequestAction<TArgs>(
  requestId: string,
  action: (args: TArgs) => Promise<unknown>,
  successText: string,
  options: { onSuccess?: () => void; onError?: (error: unknown, args: TArgs) => boolean } = {},
) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  return useMutation({
    mutationFn: action,
    onSuccess: (result) => {
      if (result && typeof result === 'object' && 'number' in result) {
        queryClient.setQueryData(queryKeys.request(requestId), result as RequestView);
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.request(requestId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.requestsAll });
      void queryClient.invalidateQueries({ queryKey: queryKeys.stagesAll });
      notify.success(successText);
      options.onSuccess?.();
    },
    onError: (error, args) => {
      if (!options.onError?.(error, args)) notify.error(error);
      if (
        isApiError(error) &&
        ['VERSION_CONFLICT', 'ILLEGAL_TRANSITION', 'STAGE_ALREADY_DECIDED'].includes(error.code)
      ) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.request(requestId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.stagesAll });
      }
    },
  });
}
