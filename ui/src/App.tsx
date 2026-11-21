import '@fontsource/golos-text/400.css';
import '@fontsource/golos-text/600.css';
import '@fontsource/golos-text/700.css';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { NotifierProvider } from './components/Notifier';
import { SessionProvider } from './session/SessionProvider';
import { ColorModeProvider } from './theme/ColorModeProvider';

/** Провайдеры приложения: тема, кэш запросов, сессия, тосты. Роутер передаётся снаружи (в тестах — память). */
export function AppProviders({ queryClient, children }: { queryClient: QueryClient; children: ReactNode }) {
  return (
    <ColorModeProvider>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <NotifierProvider>{children}</NotifierProvider>
        </SessionProvider>
      </QueryClientProvider>
    </ColorModeProvider>
  );
}
