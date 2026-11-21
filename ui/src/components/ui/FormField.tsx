import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Box from '@mui/material/Box';
import InputBase from '@mui/material/InputBase';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useId, type ChangeEvent, type ReactNode } from 'react';
import { c, radius, textStyles } from '../../theme/tokens';

// Компонент «Поле» из Figma: подпись над полем, поле 44 px с рамкой, ошибка под полем.

interface BaseProps {
  label: string;
  required?: boolean;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: ReactNode;
  testId?: string;
  disabled?: boolean;
}

const boxSx = (error: boolean, multiline: boolean) => ({
  ...textStyles.body,
  color: c('text-primary'),
  bgcolor: c('bg-surface'),
  border: `1px solid ${error ? c('sla-breach-bar') : c('border-strong')}`,
  borderRadius: `${radius.sm}px`,
  minHeight: 44,
  px: '16px',
  py: multiline ? '12px' : 0,
  alignItems: multiline ? 'flex-start' : 'center',
  transition: 'border-color 120ms',
  '&.Mui-focused': {
    borderColor: error ? c('sla-breach-bar') : c('action-primary'),
    boxShadow: `0 0 0 3px ${c('action-primary-soft')}`,
  },
  '&.Mui-disabled': { bgcolor: c('bg-surface-2') },
  '& input, & textarea, & select': { p: 0, height: multiline ? 'auto' : 42 },
  '& input::placeholder, & textarea::placeholder': { color: c('text-muted'), opacity: 1 },
});

function FieldShell({
  id,
  label,
  required,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Stack sx={{ gap: '6px', flex: 1, minWidth: 0 }}>
      <Typography variant="subtitle1" component="label" htmlFor={id}>
        {label}
        {required ? ' *' : ''}
      </Typography>
      {children}
      {error ? (
        <Typography variant="caption" sx={{ color: c('sla-breach-text') }} role="alert">
          {error}
        </Typography>
      ) : hint ? (
        <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
          {hint}
        </Typography>
      ) : null}
    </Stack>
  );
}

export function TextField({
  label,
  required,
  value,
  onChange,
  error,
  hint,
  testId,
  disabled,
  type = 'text',
  placeholder,
  autoComplete,
  inputMode,
  maxLength,
  rows,
  endAdornment,
  autoFocus,
}: BaseProps & {
  type?: string;
  placeholder?: string;
  autoComplete?: string;
  inputMode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email';
  maxLength?: number;
  /** Многострочное поле высотой ~96 px (комментарий). */
  rows?: number;
  endAdornment?: ReactNode;
  autoFocus?: boolean;
}) {
  const id = useId();
  const multiline = rows !== undefined;
  return (
    <FieldShell id={id} label={label} required={required} error={error} hint={hint}>
      <InputBase
        id={id}
        type={type}
        value={value}
        onChange={(event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value)}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        disabled={disabled}
        multiline={multiline}
        minRows={rows}
        error={Boolean(error)}
        endAdornment={endAdornment}
        inputProps={{
          'data-testid': testId,
          'aria-invalid': Boolean(error),
          'aria-required': required,
          inputMode,
          maxLength,
        }}
        sx={boxSx(Boolean(error), multiline)}
      />
    </FieldShell>
  );
}

export function SelectField({
  label,
  required,
  value,
  onChange,
  error,
  hint,
  testId,
  disabled,
  options,
  placeholder,
}: BaseProps & { options: { value: string; label: string }[]; placeholder?: string }) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} required={required} error={error} hint={hint}>
      <Box sx={{ position: 'relative' }}>
        {/* Нативный select: доступен с клавиатуры и читается экранными дикторами без доработок */}
        <Box
          component="select"
          id={id}
          value={value}
          onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange(event.target.value)}
          disabled={disabled}
          aria-invalid={Boolean(error)}
          aria-required={required}
          data-testid={testId}
          sx={{
            ...textStyles.body,
            fontFamily: 'inherit',
            width: '100%',
            height: 44,
            pl: '16px',
            pr: '40px',
            color: c('text-primary'),
            bgcolor: disabled ? c('bg-surface-2') : c('bg-surface'),
            border: `1px solid ${error ? c('sla-breach-bar') : c('border-strong')}`,
            borderRadius: `${radius.sm}px`,
            appearance: 'none',
            cursor: 'pointer',
            outline: 'none',
            '&:focus-visible': {
              borderColor: error ? c('sla-breach-bar') : c('action-primary'),
              boxShadow: `0 0 0 3px ${c('action-primary-soft')}`,
            },
            '& option': { color: c('text-primary'), bgcolor: c('bg-surface') },
          }}
        >
          {renderOptions(options, placeholder)}
        </Box>
        <ExpandMoreIcon
          sx={{
            position: 'absolute',
            right: 12,
            top: '50%',
            transform: 'translateY(-50%)',
            pointerEvents: 'none',
            color: c('text-muted'),
            fontSize: 20,
          }}
        />
      </Box>
    </FieldShell>
  );
}

function renderOptions(options: { value: string; label: string }[], placeholder?: string) {
  return (
    <>
      {placeholder !== undefined ? <option value="">{placeholder}</option> : null}
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </>
  );
}

/** Ряд полей в две колонки с отступом 20, как в формах макета. */
export function FieldRow({ children }: { children: ReactNode }) {
  return <Box sx={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>{children}</Box>;
}
