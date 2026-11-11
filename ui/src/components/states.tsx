import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useState, type ReactNode } from 'react';
import { errorMessage, isApiError } from '../api/errors';
import { texts } from '../texts/ru';

// Общие состояния экрана — ui-requirements.md §4.1.

export function LoadingState({ label = texts.common.loading }: { label?: string }) {
  return (
    <Stack spacing={2} sx={{ py: 8, alignItems: 'center', justifyContent: 'center' }} data-testid="state-loading">
      <CircularProgress size={32} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

export function EmptyState({ text, action }: { text: string; action?: ReactNode }) {
  return (
    <Stack spacing={2} sx={{ py: 8, alignItems: 'center' }} data-testid="state-empty">
      <Typography color="text.secondary">{text}</Typography>
      {action}
    </Stack>
  );
}

export function CorrelationId({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    void navigator.clipboard?.writeText(value).then(() => setCopied(true));
  };
  return (
    <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>
      {texts.common.correlationId}: <code>{value}</code>
      <Tooltip title={copied ? texts.common.copied : texts.common.copy}>
        <IconButton size="small" onClick={copy} aria-label={texts.common.copy} sx={{ ml: 0.5 }}>
          <ContentCopyIcon fontSize="inherit" />
        </IconButton>
      </Tooltip>
    </Typography>
  );
}

/** Ошибка загрузки: текст по коду, код для обращения и кнопка «Обновить». Автоповтора нет. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Box sx={{ py: 4 }} data-testid="state-error">
      <Alert
        severity="error"
        action={
          onRetry ? (
            <Button color="inherit" size="small" onClick={onRetry}>
              {texts.common.refresh}
            </Button>
          ) : undefined
        }
      >
        {errorMessage(error)}
        {isApiError(error) && error.showCorrelationId && error.correlationId ? (
          <CorrelationId value={error.correlationId} />
        ) : null}
      </Alert>
    </Box>
  );
}
