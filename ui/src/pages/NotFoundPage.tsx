import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { texts } from '../texts/ru';

export function NotFoundPage() {
  return (
    <Stack spacing={2} sx={{ alignItems: 'flex-start' }} data-testid="not-found">
      <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>
        {texts.notFound.title}
      </Typography>
      <Typography color="text.secondary">{texts.notFound.text}</Typography>
      <Button component={RouterLink} to="/" variant="outlined">
        {texts.common.backToHome}
      </Button>
    </Stack>
  );
}
