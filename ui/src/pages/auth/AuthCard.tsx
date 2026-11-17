import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { FormEvent, ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { Brand } from '../../components/ui/Brand';
import { c, radius } from '../../theme/tokens';

// Карточка входа и регистрации (UI-01, UI-02): 420 px по центру, отступы 36, между блоками 20.
export function AuthCard({
  title,
  onSubmit,
  children,
  footer,
}: {
  title: string;
  onSubmit: (event: FormEvent) => void;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: c('bg-page'), p: 2 }}>
      <Stack
        component="form"
        onSubmit={onSubmit}
        noValidate
        sx={{
          width: 420,
          maxWidth: '100%',
          gap: '20px',
          p: '36px',
          bgcolor: c('bg-surface'),
          border: `1px solid ${c('border-default')}`,
          borderRadius: `${radius.lg}px`,
        }}
      >
        <Brand />
        <Typography variant="h1" component="h1">
          {title}
        </Typography>
        {children}
        {footer}
      </Stack>
    </Box>
  );
}

/** «Нет аккаунта? Зарегистрироваться». */
export function AuthLink({ question, to, label }: { question: string; to: string; label: string }) {
  return (
    <Box sx={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
      <Typography variant="body1" component="span" sx={{ color: c('text-muted') }}>
        {question}
      </Typography>
      <Link
        component={RouterLink}
        to={to}
        variant="subtitle1"
        underline="hover"
        sx={{ color: c('action-primary-text') }}
      >
        {label}
      </Link>
    </Box>
  );
}
