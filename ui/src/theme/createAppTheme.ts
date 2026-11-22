import { ruRU } from '@mui/material/locale';
import { createTheme } from '@mui/material/styles';
import { c, colorTokens, cssVariables, radius, textStyles, type ColorMode } from './tokens';

const FONT = '"Golos Text", "Segoe UI", "Roboto", "Helvetica", "Arial", sans-serif';

// Тема MUI по макетам Figma. Цвета — CSS-переменные (tokens.ts), палитра MUI нужна только встроенным
// компонентам: фокус, индикатор загрузки, выделение в меню. Десктоп от 1366×768 (nfr.md §10).
export function createAppTheme(mode: ColorMode) {
  const hex = (token: keyof typeof colorTokens) => colorTokens[token][mode];

  return createTheme(
    {
      palette: {
        mode,
        primary: { main: hex('action-primary'), contrastText: hex('action-on-primary') },
        error: { main: hex('sla-breach-bar') },
        warning: { main: hex('sla-warn-bar') },
        success: { main: hex('sla-ok-bar') },
        background: { default: hex('bg-page'), paper: hex('bg-surface') },
        text: { primary: hex('text-primary'), secondary: hex('text-muted') },
        divider: hex('border-default'),
      },
      shape: { borderRadius: radius.sm },
      typography: {
        fontFamily: FONT,
        h1: textStyles.h1,
        h2: textStyles.h2,
        body1: textStyles.body,
        body2: textStyles.caption,
        subtitle1: textStyles.accent,
        subtitle2: textStyles.label,
        caption: textStyles.caption,
        button: { ...textStyles.button, textTransform: 'none' },
      },
      components: {
        MuiCssBaseline: {
          styleOverrides: {
            ':root': cssVariables(mode),
            body: { backgroundColor: c('bg-page'), color: c('text-primary'), fontFamily: FONT },
          },
        },
        MuiButton: {
          defaultProps: { disableElevation: true },
          styleOverrides: {
            root: {
              ...textStyles.button,
              height: 44,
              minWidth: 0,
              padding: '0 20px',
              borderRadius: radius.sm,
              whiteSpace: 'nowrap',
              flexShrink: 0,
            },
            contained: {
              backgroundColor: c('action-primary'),
              color: c('action-on-primary'),
              '&:hover': { backgroundColor: c('action-primary'), filter: 'brightness(1.08)' },
            },
            outlined: {
              backgroundColor: c('bg-surface'),
              color: c('text-primary'),
              borderColor: c('border-strong'),
              '&:hover': { backgroundColor: c('bg-surface-2'), borderColor: c('border-strong') },
            },
            text: {
              color: c('action-primary-text'),
              '&:hover': { backgroundColor: c('action-primary-soft') },
            },
          },
        },
        MuiPaper: {
          styleOverrides: { root: { backgroundImage: 'none', backgroundColor: c('bg-surface') } },
        },
        MuiDialog: {
          styleOverrides: {
            paper: { borderRadius: radius.lg, border: `1px solid ${c('border-default')}`, boxShadow: 'none' },
          },
        },
        MuiDialogTitle: { styleOverrides: { root: { ...textStyles.h2, padding: '24px 28px 8px' } } },
        MuiDialogContent: { styleOverrides: { root: { padding: '8px 28px' } } },
        MuiDialogActions: { styleOverrides: { root: { padding: '16px 28px 24px', gap: 12 } } },
        MuiMenu: {
          styleOverrides: {
            paper: { borderRadius: radius.md, border: `1px solid ${c('border-default')}`, marginTop: 6 },
          },
        },
        MuiMenuItem: { styleOverrides: { root: { ...textStyles.body, minHeight: 40 } } },
        MuiTooltip: { styleOverrides: { tooltip: { ...textStyles.caption } } },
        MuiAlert: { styleOverrides: { root: { borderRadius: radius.md, ...textStyles.body } } },
      },
    },
    ruRU,
  );
}
