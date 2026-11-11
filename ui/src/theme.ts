import { createTheme } from '@mui/material/styles';
import { ruRU } from '@mui/material/locale';

// Десктоп от 1366×768 (nfr.md §10); русская локализация встроенных компонентов MUI.
export const theme = createTheme(
  {
    palette: {
      mode: 'light',
      primary: { main: '#1f4e8c' },
      secondary: { main: '#6a3fa0' },
      background: { default: '#f5f6f8' },
    },
    shape: { borderRadius: 8 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", "Roboto", "Helvetica", "Arial", sans-serif',
      button: { textTransform: 'none', fontWeight: 600 },
    },
  },
  ruRU,
);
