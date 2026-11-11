import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { NotifierProvider } from './components/Notifier';
import { SessionProvider } from './session/SessionProvider';
import { theme } from './theme';

/** Провайдеры приложения: тема, кэш запросов, сессия, тосты. Роутер передаётся снаружи (в тестах — память). */
export function AppProviders({ queryClient, children }: { queryClient: QueryClient; children: ReactNode }) {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <NotifierProvider>{children}</NotifierProvider>
        </SessionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
