import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { Navigate, Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { errorMessage, isApiError } from '../api/errors';
import { homePath, resolveActiveRole } from '../session/roles';
import { useSession } from '../session/SessionProvider';
import { sessionStore } from '../session/storage';
import { texts } from '../texts/ru';

// UI-01. Вход (ui-requirements.md §6.1).
export function LoginPage() {
  const { status, activeRole, login } = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [expiredNotice] = useState(() => sessionStore.takeExpiredNotice());
  const [form, setForm] = useState({ login: '', password: '' });
  const [touched, setTouched] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  // Возврат только на внутренний адрес, чтобы ссылка на вход не уводила на чужой сайт
  const from = params.get('from');
  const returnTo = from && from.startsWith('/') && !from.startsWith('//') ? from : null;

  if (status === 'authenticated') return <Navigate to={returnTo ?? homePath(activeRole)} replace />;

  const loginMissing = touched && !form.login.trim();
  const passwordMissing = touched && !form.password;

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    if (!form.login.trim() || !form.password) return;
    setSubmitting(true);
    setError(null);
    try {
      const user = await login({ login: form.login.trim(), password: form.password });
      void navigate(returnTo ?? homePath(resolveActiveRole(user, sessionStore.getActiveRole())), { replace: true });
    } catch (cause) {
      setError(cause);
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', p: 2 }}>
      <Paper component="form" onSubmit={onSubmit} noValidate sx={{ p: 4, width: 400 }} elevation={2}>
        <Stack spacing={2.5}>
          <Box>
            <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>
              {texts.app.name}
            </Typography>
            <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>
              {texts.login.title}
            </Typography>
          </Box>

          {expiredNotice && !error ? (
            <Alert severity="info" data-testid="login-expired">
              {texts.session.expired}
            </Alert>
          ) : null}
          {error ? (
            <Alert severity="error" data-testid="login-error">
              {isApiError(error) && error.outcomeUnknown ? texts.login.unavailable : errorMessage(error)}
            </Alert>
          ) : null}

          <TextField
            label={texts.login.login}
            value={form.login}
            onChange={(e) => setForm({ ...form, login: e.target.value })}
            autoComplete="username"
            autoFocus
            required
            error={loginMissing}
            helperText={loginMissing ? texts.common.required : undefined}
            slotProps={{ htmlInput: { 'data-testid': 'login-login' } }}
          />
          <TextField
            label={texts.login.password}
            type={showPassword ? 'text' : 'password'}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            autoComplete="current-password"
            required
            error={passwordMissing}
            helperText={passwordMissing ? texts.common.required : undefined}
            slotProps={{
              htmlInput: { 'data-testid': 'login-password' },
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword((v) => !v)}
                      edge="end"
                      aria-label={showPassword ? texts.login.hidePassword : texts.login.showPassword}
                    >
                      {showPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />

          <Button type="submit" variant="contained" size="large" loading={submitting} data-testid="login-submit">
            {texts.login.submit}
          </Button>

          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
            {texts.login.noAccount}{' '}
            <Link component={RouterLink} to="/register">
              {texts.login.register}
            </Link>
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
}
