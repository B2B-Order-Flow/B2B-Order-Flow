import Button from '@mui/material/Button';
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { errorMessage, fieldErrors } from '../api/errors';
import { authApi } from '../api/endpoints';
import { TextField } from '../components/ui/FormField';
import { Notice } from '../components/ui/Notice';
import { homePath } from '../session/roles';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { AuthCard, AuthLink } from './auth/AuthCard';

type Form = { login: string; password: string; passwordRepeat: string; fullName: string; email: string };
type Errors = Partial<Record<keyof Form, string>>;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Проверки формата до отправки — правила api-contract.md §10.2. */
function validate(form: Form): Errors {
  const t = texts.register.errors;
  const errors: Errors = {};
  const login = form.login.trim();
  if (!login) errors.login = texts.common.required;
  else if (login.length < 3 || login.length > 100) errors.login = t.login;
  if (!form.password) errors.password = texts.common.required;
  else if (form.password.length < 8 || form.password.length > 100) errors.password = t.password;
  if (!form.passwordRepeat) errors.passwordRepeat = texts.common.required;
  else if (form.passwordRepeat !== form.password) errors.passwordRepeat = t.passwordRepeat;
  if (!form.fullName.trim()) errors.fullName = texts.common.required;
  else if (form.fullName.trim().length > 255) errors.fullName = t.tooLong;
  if (!form.email.trim()) errors.email = texts.common.required;
  else if (!EMAIL.test(form.email.trim()) || form.email.trim().length > 255) errors.email = t.email;
  return errors;
}

// UI-02. Регистрация пользователя (ui-requirements.md §6.2, макет «UI-02 Регистрация»).
// После успеха — автоматический вход и регистрация компании (UI-03).
export function RegisterPage() {
  const { status, activeRole, login } = useSession();
  const navigate = useNavigate();
  const [form, setForm] = useState<Form>({ login: '', password: '', passwordRepeat: '', fullName: '', email: '' });
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated' && !submitting) return <Navigate to={homePath(activeRole)} replace />;

  const set = (key: keyof Form) => (value: string) => setForm((current) => ({ ...current, [key]: value }));

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const local = validate(form);
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setSubmitting(true);
    setError(null);
    const credentials = { login: form.login.trim(), password: form.password };
    try {
      await authApi.register({ ...credentials, fullName: form.fullName.trim(), email: form.email.trim() });
      await login(credentials);
      void navigate('/company/new', { replace: true, state: { justRegistered: true } });
    } catch (cause) {
      setErrors(fieldErrors(cause) as Errors);
      setError(cause);
      setSubmitting(false);
    }
  };

  return (
    <AuthCard
      title={texts.register.title}
      onSubmit={onSubmit}
      footer={<AuthLink question={texts.register.haveAccount} to="/login" label={texts.register.signIn} />}
    >
      {error ? (
        <Notice tone="error" testId="register-error">
          {errorMessage(error, { mutation: true })}
        </Notice>
      ) : null}
      <TextField
        label={texts.register.login}
        required
        value={form.login}
        onChange={set('login')}
        autoComplete="username"
        autoFocus
        error={errors.login}
        testId="register-login"
      />
      <TextField
        label={texts.register.password}
        required
        type="password"
        value={form.password}
        onChange={set('password')}
        autoComplete="new-password"
        error={errors.password}
        testId="register-password"
      />
      <TextField
        label={texts.register.passwordRepeat}
        required
        type="password"
        value={form.passwordRepeat}
        onChange={set('passwordRepeat')}
        autoComplete="new-password"
        error={errors.passwordRepeat}
        testId="register-password-repeat"
      />
      <TextField
        label={texts.register.fullName}
        required
        value={form.fullName}
        onChange={set('fullName')}
        autoComplete="name"
        error={errors.fullName}
        testId="register-full-name"
      />
      <TextField
        label={texts.register.email}
        required
        type="email"
        inputMode="email"
        value={form.email}
        onChange={set('email')}
        autoComplete="email"
        error={errors.email}
        testId="register-email"
      />
      <Button type="submit" variant="contained" fullWidth loading={submitting} data-testid="register-submit">
        {texts.register.submit}
      </Button>
    </AuthCard>
  );
}
