import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './errors';

// Повторяются только чтения и только при сбое связи (retryable); запись не повторяется никогда
// (api-contract.md §5.1, ui-requirements.md §2 п. 5).
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => isApiError(error) && error.retryable && failureCount < 1,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
