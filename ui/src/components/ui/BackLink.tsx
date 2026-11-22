import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { c } from '../../theme/tokens';

/** Ссылка «назад» над заголовком карточки: «Все заявки». */
export function BackLink({ to, children }: { to: string; children: string }) {
  return (
    <Typography
      component={RouterLink}
      to={to}
      variant="subtitle1"
      sx={{
        color: c('action-primary-text'),
        textDecoration: 'none',
        alignSelf: 'flex-start',
        '&:hover': { textDecoration: 'underline' },
      }}
    >
      {children}
    </Typography>
  );
}
