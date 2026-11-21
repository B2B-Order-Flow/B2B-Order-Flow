import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { companiesApi, queryKeys } from '../api/endpoints';
import { errorMessage, isApiError } from '../api/errors';
import { newIdempotencyKey } from '../api/client';
import type { CompanyView } from '../api/types';
import { CompanyStatusBadge } from '../components/StatusBadge';
import { FieldRow, TextField } from '../components/ui/FormField';
import { Notice } from '../components/ui/Notice';
import { Columns, Muted, Page, PageTitle, Panel } from '../components/ui/Panel';
import { isValidInn, isValidKpp, isValidOgrn } from '../lib/requisites';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { c } from '../theme/tokens';

type Form = { name: string; inn: string; kpp: string; ogrn: string; legalAddress: string };
type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = { name: '', inn: '', kpp: '', ogrn: '', legalAddress: '' };
const t = texts.companyNew;

function validate(form: Form): Errors {
  const errors: Errors = {};
  if (!form.name.trim()) errors.name = texts.common.required;
  else if (form.name.trim().length > 500) errors.name = t.errors.tooLong;
  if (!form.inn.trim()) errors.inn = texts.common.required;
  else if (!isValidInn(form.inn.trim())) errors.inn = t.errors.inn;
  if (form.kpp.trim() && !isValidKpp(form.kpp.trim())) errors.kpp = t.errors.kpp;
  if (form.ogrn.trim() && !isValidOgrn(form.ogrn.trim())) errors.ogrn = t.errors.ogrn;
  if (!form.legalAddress.trim()) errors.legalAddress = texts.common.required;
  else if (form.legalAddress.trim().length > 500) errors.legalAddress = t.errors.tooLong;
  return errors;
}

/** Ошибки полей по ответу: нарушения из violations, ИНН из справочника — под полем ИНН. */
function serverFieldErrors(error: unknown): Errors {
  if (!isApiError(error)) return {};
  const result: Errors = {};
  for (const v of error.violations) {
    if (!(v.field in EMPTY)) continue;
    result[v.field as keyof Form] = v.code === 'LIQUIDATED' ? t.errors.liquidated : v.message;
  }
  if (error.code === 'COMPANY_INN_NOT_FOUND') result.inn = t.errors.innNotFound;
  if (error.code === 'COMPANY_INN_EXISTS') result.inn = t.errors.innExists;
  return result;
}

// UI-03. Регистрация компании (ui-requirements.md §6.3, макет «UI-03 Регистрация компании»).
export function CompanyNewPage() {
  const { user, company } = useSession();
  const [resubmit, setResubmit] = useState(false);

  if (company && !resubmit) {
    return <CompanyStatusView companyId={company.id} onResubmit={() => setResubmit(true)} />;
  }
  return <CompanyForm login={user?.login ?? ''} prefillFrom={resubmit ? company?.id : undefined} />;
}

function CompanyForm({ login, prefillFrom }: { login: string; prefillFrom?: string }) {
  const queryClient = useQueryClient();
  const previous = useQuery({
    queryKey: queryKeys.company(prefillFrom ?? ''),
    queryFn: () => companiesApi.get(prefillFrom!),
    enabled: Boolean(prefillFrom),
  });
  const [form, setForm] = useState<Form | null>(prefillFrom ? null : EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const idempotencyKey = useRef(newIdempotencyKey());

  // Повторная подача: форма заполняется реквизитами отклонённой компании (SC-00 A1)
  const current: Form =
    form ??
    (previous.data
      ? {
          name: previous.data.name,
          inn: previous.data.inn,
          kpp: previous.data.kpp ?? '',
          ogrn: previous.data.ogrn ?? '',
          legalAddress: previous.data.legalAddress ?? '',
        }
      : EMPTY);

  const set = (key: keyof Form) => (value: string) => {
    // Другие данные — другой запрос: новый ключ идемпотентности
    idempotencyKey.current = newIdempotencyKey();
    setForm({ ...current, [key]: value });
  };

  const submit = async () => {
    const local = validate(current);
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await companiesApi.register(
        {
          name: current.name.trim(),
          inn: current.inn.trim(),
          kpp: current.kpp.trim() || undefined,
          ogrn: current.ogrn.trim() || undefined,
          legalAddress: current.legalAddress.trim(),
        },
        idempotencyKey.current,
      );
      await queryClient.invalidateQueries({ queryKey: queryKeys.me });
    } catch (cause) {
      setErrors(serverFieldErrors(cause));
      setError(cause);
    } finally {
      setSubmitting(false);
    }
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void submit();
  };

  const retryable = isApiError(error) && error.code === 'EXTERNAL_SYSTEM_UNAVAILABLE';

  return (
    <Page testId="company-new">
      <PageTitle>{t.title}</PageTitle>
      <Muted>{t.intro}</Muted>
      <Columns>
        <Panel component="form" onSubmit={onSubmit} noValidate gap={20} px={32} py={28} sx={{ flex: 1 }}>
          {error ? (
            <Notice
              tone="error"
              testId="company-new-error"
              action={
                retryable ? (
                  <Button variant="outlined" onClick={() => void submit()} sx={{ height: 36 }}>
                    {texts.common.retry}
                  </Button>
                ) : undefined
              }
            >
              <Typography variant="subtitle1" component="span" sx={{ color: 'inherit' }}>
                {errorMessage(error, { mutation: true })}
              </Typography>
            </Notice>
          ) : null}
          <FieldRow>
            <TextField
              label={t.name}
              required
              value={current.name}
              onChange={set('name')}
              error={errors.name}
              testId="company-new-name"
            />
            <TextField
              label={t.inn}
              required
              value={current.inn}
              onChange={set('inn')}
              error={errors.inn}
              inputMode="numeric"
              maxLength={12}
              testId="company-new-inn"
            />
          </FieldRow>
          <FieldRow>
            <TextField
              label={t.kpp}
              value={current.kpp}
              onChange={set('kpp')}
              error={errors.kpp}
              inputMode="numeric"
              maxLength={9}
              testId="company-new-kpp"
            />
            <TextField
              label={t.ogrn}
              value={current.ogrn}
              onChange={set('ogrn')}
              error={errors.ogrn}
              inputMode="numeric"
              maxLength={15}
              testId="company-new-ogrn"
            />
          </FieldRow>
          <TextField
            label={t.legalAddress}
            required
            value={current.legalAddress}
            onChange={set('legalAddress')}
            error={errors.legalAddress}
            testId="company-new-legal-address"
          />
          <Box sx={{ display: 'flex', gap: '12px' }}>
            <Button type="submit" variant="contained" loading={submitting} data-testid="company-new-submit">
              {t.submit}
            </Button>
            <Button
              variant="text"
              onClick={() => {
                setForm(EMPTY);
                setErrors({});
                setError(null);
              }}
            >
              {texts.common.cancel}
            </Button>
          </Box>
        </Panel>
        <LoginHint login={login} />
      </Columns>
    </Page>
  );
}

/** Боковая панель «Ваш логин»: логин передают владельцу уже зарегистрированной компании (SC-00, шаг 4). */
function LoginHint({ login }: { login: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Panel tone="soft" gap={8} px={24} py={24} sx={{ width: 340, flexShrink: 0 }} data-testid="login-hint">
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
        <Typography variant="subtitle1" component="div">
          {t.yourLogin(login)}
        </Typography>
        <Tooltip title={copied ? texts.common.copied : texts.common.copy}>
          <IconButton
            size="small"
            aria-label={t.copyLogin}
            onClick={() => void navigator.clipboard?.writeText(login).then(() => setCopied(true))}
            sx={{ color: c('action-primary-text') }}
          >
            <ContentCopyIcon sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
      </Box>
      <Typography variant="caption" sx={{ color: c('text-muted') }}>
        {t.loginHint}
      </Typography>
    </Panel>
  );
}

/** Компания уже отправлена: «На подтверждении» или «Отклонена» с причиной и повторной подачей (EC-14, SC-00 A1). */
function CompanyStatusView({ companyId, onResubmit }: { companyId: string; onResubmit: () => void }) {
  const { user, company } = useSession();
  const details = useQuery({
    queryKey: queryKeys.company(companyId),
    queryFn: () => companiesApi.get(companyId),
    retry: false,
  });
  const view: Partial<CompanyView> = details.data ?? company ?? {};
  const rejected = view.status === 'REJECTED';

  return (
    <Page testId="company-status">
      <PageTitle>{t.title}</PageTitle>
      <Muted>{rejected ? t.rejectedIntro : t.pendingIntro}</Muted>
      <Columns>
        <Panel gap={16} px={32} py={28} sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Typography variant="h2" component="h2">
              {view.name}
            </Typography>
            {view.status ? <CompanyStatusBadge status={view.status} /> : null}
          </Box>
          {rejected ? (
            <>
              <Notice tone="error" title={t.rejectReason}>
                {view.rejectReason ?? t.rejectReasonUnknown}
              </Notice>
              <Box>
                <Button variant="contained" onClick={onResubmit} data-testid="company-new-resubmit">
                  {t.resubmit}
                </Button>
              </Box>
            </>
          ) : (
            <Typography variant="body1" sx={{ color: c('text-secondary') }}>
              {t.pendingText}
            </Typography>
          )}
        </Panel>
        <LoginHint login={user?.login ?? ''} />
      </Columns>
    </Page>
  );
}
