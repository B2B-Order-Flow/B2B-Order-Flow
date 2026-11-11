import Alert, { type AlertColor } from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { errorMessage } from '../api/errors';

// Короткие сообщения после действий (тосты) — ui-requirements.md §4.1 «Действие выполнено».

interface Toast {
  id: number;
  severity: AlertColor;
  text: string;
}

interface Notifier {
  success: (text: string) => void;
  info: (text: string) => void;
  /** Ошибка изменяющего действия: текст по коду, при обрыве связи — «исход неизвестен». */
  error: (error: unknown) => void;
}

const NotifierContext = createContext<Notifier | null>(null);

export function NotifierProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null);

  const show = useCallback((severity: AlertColor, text: string) => {
    setToast({ id: Date.now(), severity, text });
  }, []);

  const value = useMemo<Notifier>(
    () => ({
      success: (text) => show('success', text),
      info: (text) => show('info', text),
      error: (error) => show('error', errorMessage(error, { mutation: true })),
    }),
    [show],
  );

  return (
    <NotifierContext.Provider value={value}>
      {children}
      <Snackbar
        key={toast?.id}
        open={toast !== null}
        autoHideDuration={toast?.severity === 'error' ? 8000 : 4000}
        onClose={(_, reason) => reason !== 'clickaway' && setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        {toast ? (
          <Alert severity={toast.severity} variant="filled" onClose={() => setToast(null)} data-testid="toast">
            {toast.text}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotifierContext.Provider>
  );
}

export function useNotify(): Notifier {
  const notifier = useContext(NotifierContext);
  if (!notifier) throw new Error('useNotify вне NotifierProvider');
  return notifier;
}
