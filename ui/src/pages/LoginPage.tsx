import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { errorMessage, isApiError } from '../api/errors';
import { TextField } from '../components/ui/FormField';
import { Notice } from '../components/ui/Notice';
import { homePath, resolveActiveRole } from '../session/roles';
import { useSession } from '../session/SessionProvider';
import { sessionStore } from '../session/storage';
import { texts } from '../texts/ru';
import { c } from '../theme/tokens';
import { AuthCard, AuthLink } from './auth/AuthCard';

// UI-01. Вход (ui-requirements.md §6.1, макет «UI-01 Вход»).
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
    <AuthCard
      title={texts.login.title}
      onSubmit={onSubmit}
      footer={<AuthLink question={texts.login.noAccount} to="/register" label={texts.login.register} />}
    >
      {expiredNotice && !error ? (
        <Notice tone="info" testId="login-expired">
          {texts.session.expired}
        </Notice>
      ) : null}
      {error ? (
        <Notice tone="error" testId="login-error">
          {isApiError(error) && error.outcomeUnknown ? texts.login.unavailable : errorMessage(error)}
        </Notice>
      ) : null}

      <TextField
        label={texts.login.login}
        required
        value={form.login}
        onChange={(value) => setForm({ ...form, login: value })}
        autoComplete="username"
        autoFocus
        error={loginMissing ? texts.common.required : undefined}
        testId="login-login"
      />
      <TextField
        label={texts.login.password}
        required
        type={showPassword ? 'text' : 'password'}
        value={form.password}
        onChange={(value) => setForm({ ...form, password: value })}
        autoComplete="current-password"
        error={passwordMissing ? texts.common.required : undefined}
        testId="login-password"
        endAdornment={
          <IconButton
            size="small"
            edge="end"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? texts.login.hidePassword : texts.login.showPassword}
            sx={{ color: c('text-muted') }}
          >
            {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
          </IconButton>
        }
      />

      <Button type="submit" variant="contained" fullWidth loading={submitting} data-testid="login-submit">
        {texts.login.submit}
      </Button>
    </AuthCard>
  );
}
