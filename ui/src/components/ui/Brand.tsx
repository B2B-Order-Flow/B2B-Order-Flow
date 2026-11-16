import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { texts } from '../../texts/ru';
import { c, radius, textStyles } from '../../theme/tokens';

/** Логотип: круг цвета действия и название. */
export function Brand() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexShrink: 0 }}>
      <Box aria-hidden sx={{ width: 26, height: 26, borderRadius: '50%', bgcolor: c('action-primary') }} />
      <Typography variant="subtitle1" component="span" sx={{ color: c('text-primary') }}>
        {texts.app.name}
      </Typography>
    </Box>
  );
}

/** Инициалы: «Алексей Соколов» → «АС». */
export function initials(fullName: string | undefined): string {
  if (!fullName) return '?';
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

export function Avatar({ name, size = 40 }: { name: string | undefined; size?: number }) {
  return (
    <Box
      aria-hidden
      sx={{
        ...textStyles.label,
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: `${radius.full}px`,
        bgcolor: c('action-primary-soft'),
        color: c('action-primary-text'),
        display: 'grid',
        placeItems: 'center',
      }}
    >
      {initials(name)}
    </Box>
  );
}
