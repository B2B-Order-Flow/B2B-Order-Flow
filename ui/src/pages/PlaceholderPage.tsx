import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { texts } from '../texts/ru';

// Заглушка экрана, который ещё не реализован. id — номер экрана из ui-requirements.md (UI-xx).
export function PlaceholderPage({ id, title }: { id: string; title: string }) {
  return (
    <>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 700, mb: 2 }}>
        {title}
      </Typography>
      <Paper variant="outlined" sx={{ p: 3 }} data-testid={`placeholder-${id.toLowerCase()}`}>
        <Typography color="text.secondary">
          {id}. {texts.common.notImplemented}
        </Typography>
      </Paper>
    </>
  );
}
