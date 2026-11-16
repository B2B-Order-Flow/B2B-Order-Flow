import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { createAppTheme } from './createAppTheme';
import type { ColorMode } from './tokens';

// Светлая и тёмная темы. Выбор — удобство одного зрителя, поэтому хранится в localStorage
// (может быть недоступен — тогда тема просто не запоминается).

const STORAGE_KEY = 'b2b.colorMode';

function readStoredMode(): ColorMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

interface ColorModeContextValue {
  mode: ColorMode;
  toggle: () => void;
}

const ColorModeContext = createContext<ColorModeContextValue | null>(null);

export function ColorModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ColorMode>(readStoredMode);

  const toggle = useCallback(() => {
    setMode((current) => {
      const next = current === 'light' ? 'dark' : 'light';
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // хранилище недоступно — тема действует до перезагрузки
      }
      return next;
    });
  }, []);

  const theme = useMemo(() => createAppTheme(mode), [mode]);
  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode(): ColorModeContextValue {
  const value = useContext(ColorModeContext);
  if (!value) throw new Error('useColorMode вне ColorModeProvider');
  return value;
}
