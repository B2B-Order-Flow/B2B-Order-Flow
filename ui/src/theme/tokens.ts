// Цвета и размеры из макетов Figma (стиль B «тёплый деловой»): переменные «Цвет» (светлая тема),
// «Цвет · тёмная тема» и «Размеры». Имена CSS-переменных совпадают с кодом переменных в Figma
// (Dev Mode показывает var(--bg-page) и т. п.), поэтому значения из макета переносятся один к одному.

export type ColorMode = 'light' | 'dark';

export const colorTokens = {
  'bg-page': { light: '#F4F2ED', dark: '#141210' },
  'bg-surface': { light: '#FFFFFF', dark: '#26231F' },
  'bg-surface-2': { light: '#F0EDE6', dark: '#302C27' },
  'border-default': { light: '#E3DED3', dark: '#47413A' },
  'border-strong': { light: '#D6D0C4', dark: '#5F5850' },
  'text-primary': { light: '#1F1D19', dark: '#F1ECE3' },
  'text-secondary': { light: '#3A362F', dark: '#D6CEC0' },
  'text-muted': { light: '#67625A', dark: '#B3AA9B' },
  'action-primary': { light: '#3341A0', dark: '#A7B0F5' },
  'action-on-primary': { light: '#FFFFFF', dark: '#141A4A' },
  'action-primary-soft': { light: '#E6E8F7', dark: '#2B2F57' },
  'action-primary-text': { light: '#2F3C93', dark: '#CBD1FF' },
  'status-neutral-bg': { light: '#ECE8DF', dark: '#332F28' },
  'status-neutral-fg': { light: '#4B463E', dark: '#D6CEC0' },
  'status-indigo-bg': { light: '#E6E8F7', dark: '#2B2F57' },
  'status-indigo-fg': { light: '#2F3C93', dark: '#CBD1FF' },
  'status-purple-bg': { light: '#EEE7F8', dark: '#34284D' },
  'status-purple-fg': { light: '#5B3A9E', dark: '#D2BEF7' },
  'status-orange-bg': { light: '#FBE3D2', dark: '#3E2416' },
  'status-orange-fg': { light: '#8A3D0A', dark: '#F5B48A' },
  'status-ochre-bg': { light: '#FBEBC4', dark: '#3B2E12' },
  'status-ochre-fg': { light: '#6E4A00', dark: '#F0C46A' },
  'status-green-bg': { light: '#DDEFE3', dark: '#1B3527' },
  'status-green-fg': { light: '#23603F', dark: '#8ED3A8' },
  'status-red-bg': { light: '#F8E0DC', dark: '#3D1F1B' },
  'status-red-fg': { light: '#9B2C24', dark: '#F3A39A' },
  'status-cancelled-bg': { light: '#D9D4CA', dark: '#2A2622' },
  'status-cancelled-fg': { light: '#3A362F', dark: '#B3AA9B' },
  'sla-track': { light: '#ECE8DF', dark: '#332F28' },
  'sla-ok-bar': { light: '#2F8A57', dark: '#5DBB86' },
  'sla-ok-text': { light: '#23603F', dark: '#8ED3A8' },
  'sla-warn-bar': { light: '#C98A0E', dark: '#E0A93A' },
  'sla-warn-text': { light: '#6E4A00', dark: '#F0C46A' },
  'sla-breach-bar': { light: '#B83A30', dark: '#E06A5E' },
  'sla-breach-text': { light: '#9B2C24', dark: '#F3A39A' },
  'sla-paused': { light: '#D6D0C4', dark: '#4A443B' },
} as const satisfies Record<string, Record<ColorMode, string>>;

export type ColorToken = keyof typeof colorTokens;

/** Ссылка на цвет для sx и стилей: `c('bg-surface')` → `var(--bg-surface)`. */
export const c = (token: ColorToken) => `var(--${token})`;

/** CSS-переменные одной темы. */
export function cssVariables(mode: ColorMode): Record<string, string> {
  return Object.fromEntries(Object.entries(colorTokens).map(([name, value]) => [`--${name}`, value[mode]]));
}

/** Коллекция «Размеры»: скругления и шаги отступов, px. */
export const radius = { sm: 10, md: 12, lg: 16, full: 999 } as const;

/** Ширина содержимого: макет 1440 px с полями по 120 px. */
export const CONTENT_WIDTH = 1200;

/** Стили текста Figma: «Заголовок/H1», «Текст/Основной» и т. д. Шрифт — Golos Text. */
export const textStyles = {
  h1: { fontSize: 32, fontWeight: 700, lineHeight: 1.15, letterSpacing: '-0.02em' },
  h2: { fontSize: 20, fontWeight: 700, lineHeight: 1.3 },
  body: { fontSize: 15, fontWeight: 400, lineHeight: 1.5 },
  accent: { fontSize: 15, fontWeight: 600, lineHeight: 1.5 },
  caption: { fontSize: 13, fontWeight: 400, lineHeight: 1.45 },
  label: { fontSize: 13, fontWeight: 600, lineHeight: 1.4 },
  button: { fontSize: 15, fontWeight: 600, lineHeight: 1.2 },
} as const;
