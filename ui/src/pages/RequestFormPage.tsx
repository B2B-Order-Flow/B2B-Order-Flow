import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { newIdempotencyKey } from '../api/client';
import { queryKeys, requestsApi } from '../api/endpoints';
import { errorMessage, fieldErrors, isApiError } from '../api/errors';
import type { ContractType, RequestPayload, RequestView } from '../api/types';
import { useNotify } from '../components/Notifier';
import { Chip } from '../components/StatusBadge';
import { FieldRow, SelectField, TextField } from '../components/ui/FormField';
import { Notice } from '../components/ui/Notice';
import { Columns, Muted, Page, PageTitle, Panel, PanelTitle } from '../components/ui/Panel';
import { ErrorState, LoadingState } from '../components/states';
import { isValidPhone } from '../lib/requisites';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { c, radius, textStyles } from '../theme/tokens';

const t = texts.requestForm;

type Form = {
  address: string;
  speed: string;
  desiredDate: string;
  contactName: string;
  contactPhone: string;
  contractPreference: ContractType | '';
  clientComment: string;
};
type FieldKey = keyof Form;
type Errors = Partial<Record<FieldKey, string>>;

// Имена полей формы ↔ имена в API (для violations)
const API_FIELD: Record<string, FieldKey> = {
  address: 'address',
  requestedSpeedMbps: 'speed',
  desiredDate: 'desiredDate',
  contactName: 'contactName',
  contactPhone: 'contactPhone',
  contractPreference: 'contractPreference',
  clientComment: 'clientComment',
};

const today = () => {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};

function fromRequest(request: RequestView | undefined, fallbackContact: string): Form {
  return {
    address: request?.address ?? '',
    speed: request?.requestedSpeedMbps ? String(request.requestedSpeedMbps) : '',
    desiredDate: request?.desiredDate ?? '',
    contactName: request?.contactName ?? fallbackContact,
    contactPhone: request?.contactPhone ?? '',
    contractPreference: request?.contractPreference ?? '',
    clientComment: request?.clientComment ?? '',
  };
}

function toPayload(form: Form): RequestPayload {
  const text = (value: string) => value.trim() || undefined;
  return {
    address: text(form.address),
    requestedSpeedMbps: form.speed.trim() ? Number(form.speed.trim()) : undefined,
    desiredDate: form.desiredDate || undefined,
    contactName: text(form.contactName),
    contactPhone: text(form.contactPhone),
    contractPreference: form.contractPreference || undefined,
    clientComment: text(form.clientComment),
  };
}

/** Формат заполненных полей (api-contract.md §10.6); при отправке — ещё и обязательность. */
function validate(form: Form, forSubmit: boolean): Errors {
  const errors: Errors = {};
  const required: FieldKey[] = ['address', 'speed', 'desiredDate', 'contactName', 'contactPhone'];
  if (forSubmit) for (const key of required) if (!form[key].trim()) errors[key] = texts.common.required;
  if (form.address.trim().length > 500) errors.address = t.errors.tooLong(500);
  const speed = form.speed.trim();
  if (speed && (!/^\d+$/.test(speed) || Number(speed) < 1 || Number(speed) > 100_000)) errors.speed = t.errors.speed;
  if (form.desiredDate && form.desiredDate < today()) errors.desiredDate = t.errors.date;
  if (form.contactName.trim().length > 255) errors.contactName = t.errors.tooLong(255);
  if (form.contactPhone.trim() && !isValidPhone(form.contactPhone.trim())) errors.contactPhone = t.errors.phone;
  if (form.clientComment.length > 4000) errors.clientComment = t.errors.tooLong(4000);
  return errors;
}

function mapViolations(error: unknown): Errors {
  const raw = fieldErrors(error);
  const result: Errors = {};
  for (const [field, message] of Object.entries(raw)) {
    const key = API_FIELD[field];
    if (key) result[key] = message;
  }
  return result;
}

const timeFormat = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });

// UI-07. Форма заявки (ui-requirements.md §6.7, макет «UI-07 Форма заявки»): новый черновик и правка.
export function RequestFormPage() {
  const { requestId } = useParams();
  const query = useQuery({
    queryKey: queryKeys.request(requestId ?? ''),
    queryFn: () => requestsApi.get(requestId!),
    enabled: Boolean(requestId),
  });

  if (!requestId) return <RequestForm key="new" />;
  if (query.isPending) return <LoadingState />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  if (query.data.status !== 'DRAFT') return <Navigate to={`/requests/${requestId}`} replace />;
  return <RequestForm key={requestId} draft={query.data} />;
}

function RequestForm({ draft }: { draft?: RequestView }) {
  const { user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [form, setForm] = useState<Form>(() => fromRequest(draft, user?.fullName ?? ''));
  const [saved, setSaved] = useState<{ id: string; version: number; at: Date | null } | null>(
    draft ? { id: draft.id, version: draft.version, at: draft.updatedAt ? new Date(draft.updatedAt) : null } : null,
  );
  const [dirty, setDirty] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<'save' | 'submit' | 'delete' | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const createKey = useRef(newIdempotencyKey());

  const set = (key: FieldKey) => (value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setDirty(true);
    if (!saved) createKey.current = newIdempotencyKey();
  };

  /** Сохраняет черновик: создание (Idempotency-Key) или правка (version). Возвращает актуальную заявку. */
  const save = async (): Promise<RequestView> => {
    const payload = toPayload(form);
    const result = saved
      ? await requestsApi.updateDraft(saved.id, { ...payload, version: saved.version })
      : await requestsApi.create(payload, createKey.current);
    setSaved({ id: result.id, version: result.version, at: new Date() });
    setDirty(false);
    queryClient.setQueryData(queryKeys.request(result.id), result);
    void queryClient.invalidateQueries({ queryKey: queryKeys.requestsAll });
    return result;
  };

  const handleError = async (cause: unknown) => {
    setError(cause);
    setErrors(mapViolations(cause));
    // Черновик изменили в другой вкладке: перечитываем версию, правки пользователя остаются в форме
    if (isApiError(cause) && cause.code === 'VERSION_CONFLICT' && saved) {
      const fresh = await requestsApi.get(saved.id).catch(() => null);
      if (fresh) setSaved({ id: fresh.id, version: fresh.version, at: saved.at });
    }
  };

  const onSave = async () => {
    const local = validate(form, false);
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setBusy('save');
    setError(null);
    try {
      const result = await save();
      notify.success(t.saved);
      if (!draft) void navigate(`/requests/${result.id}/edit`, { replace: true });
    } catch (cause) {
      await handleError(cause);
    } finally {
      setBusy(null);
    }
  };

  const onSubmit = async () => {
    const local = validate(form, true);
    setErrors(local);
    if (Object.keys(local).length > 0) {
      setError(null);
      return;
    }
    setBusy('submit');
    setError(null);
    try {
      // «Отправить оператору» сначала сохраняет несохранённые изменения (§6.7)
      const current = !saved || dirty ? await save() : { id: saved.id, version: saved.version };
      const submitted = await requestsApi.submit(current.id, current.version);
      queryClient.setQueryData(queryKeys.request(submitted.id), submitted);
      void queryClient.invalidateQueries({ queryKey: queryKeys.requestsAll });
      notify.success(t.submitted);
      for (const warning of submitted.warnings ?? []) {
        const text = texts.warnings[warning.code];
        if (text && warning.code === 'DUPLICATE_REQUEST_WARNING') notify.info(text);
      }
      void navigate(`/requests/${submitted.id}`, { replace: true });
    } catch (cause) {
      await handleError(cause);
      setBusy(null);
    }
  };

  const onDelete = async () => {
    if (!saved) return;
    setBusy('delete');
    try {
      await requestsApi.deleteDraft(saved.id, saved.version);
      void queryClient.invalidateQueries({ queryKey: queryKeys.requestsAll });
      notify.success(t.deleted);
      void navigate('/requests', { replace: true });
    } catch (cause) {
      setConfirmDelete(false);
      await handleError(cause);
      setBusy(null);
    }
  };

  const errorText = (() => {
    if (!error) return null;
    if (isApiError(error) && error.code === 'VALIDATION_FAILED') return t.incomplete;
    if (isApiError(error) && error.code === 'COMPANY_NOT_CONFIRMED') return t.companyNotConfirmed;
    return errorMessage(error, { mutation: true });
  })();

  return (
    <Page testId="request-form">
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <PageTitle>{t.title}</PageTitle>
          <Chip tone="neutral" label={texts.requestStatus.DRAFT.label} />
        </Box>
        <Muted>{saved?.at ? t.savedAt(timeFormat.format(saved.at)) : t.checkOnSubmit}</Muted>
      </Box>
      <Columns>
        <Panel
          component="form"
          noValidate
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            void onSubmit();
          }}
          gap={20}
          sx={{ flex: 1 }}
        >
          {errorText ? (
            <Notice tone="error" testId="request-form-error">
              {errorText}
            </Notice>
          ) : null}
          <TextField
            label={t.address}
            required
            value={form.address}
            onChange={set('address')}
            placeholder={t.addressPlaceholder}
            error={errors.address}
            maxLength={500}
            testId="request-form-address"
          />
          <FieldRow>
            <TextField
              label={t.speed}
              required
              value={form.speed}
              onChange={set('speed')}
              inputMode="numeric"
              error={errors.speed}
              testId="request-form-speed"
            />
            <TextField
              label={t.desiredDate}
              required
              type="date"
              value={form.desiredDate}
              onChange={set('desiredDate')}
              error={errors.desiredDate}
              testId="request-form-date"
            />
          </FieldRow>
          <FieldRow>
            <TextField
              label={t.contactName}
              required
              value={form.contactName}
              onChange={set('contactName')}
              error={errors.contactName}
              autoComplete="name"
              testId="request-form-contact-name"
            />
            <TextField
              label={t.contactPhone}
              required
              type="tel"
              inputMode="tel"
              value={form.contactPhone}
              onChange={set('contactPhone')}
              placeholder={t.phonePlaceholder}
              error={errors.contactPhone}
              autoComplete="tel"
              testId="request-form-contact-phone"
            />
          </FieldRow>
          <SelectField
            label={t.contractPreference}
            value={form.contractPreference}
            onChange={set('contractPreference')}
            placeholder={t.contractNotChosen}
            options={(['OFFER', 'STANDARD', 'CLIENT_FORM'] as const).map((value) => ({
              value,
              label: texts.contractType[value],
            }))}
            error={errors.contractPreference}
            testId="request-form-contract"
          />
          <TextField
            label={t.comment}
            value={form.clientComment}
            onChange={set('clientComment')}
            placeholder={t.commentPlaceholder}
            rows={3}
            maxLength={4000}
            error={errors.clientComment}
            hint={form.clientComment ? t.counter(form.clientComment.length) : undefined}
            testId="request-form-comment"
          />
          <Box sx={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <Button
              type="submit"
              variant="contained"
              loading={busy === 'submit'}
              disabled={busy !== null && busy !== 'submit'}
              data-testid="request-form-submit"
            >
              {t.submit}
            </Button>
            <Button
              variant="outlined"
              onClick={() => void onSave()}
              loading={busy === 'save'}
              disabled={busy !== null && busy !== 'save'}
              data-testid="request-form-save"
            >
              {t.save}
            </Button>
            {saved ? (
              <Button
                variant="text"
                onClick={() => setConfirmDelete(true)}
                disabled={busy !== null}
                data-testid="request-form-delete"
              >
                {t.delete}
              </Button>
            ) : null}
          </Box>
        </Panel>
        <NextSteps />
      </Columns>

      <Dialog open={confirmDelete} onClose={() => setConfirmDelete(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t.deleteTitle}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {t.deleteText}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="text" onClick={() => setConfirmDelete(false)}>
            {texts.common.cancel}
          </Button>
          <Button
            variant="contained"
            loading={busy === 'delete'}
            onClick={() => void onDelete()}
            data-testid="request-form-delete-confirm"
          >
            {t.delete}
          </Button>
        </DialogActions>
      </Dialog>
    </Page>
  );
}

/** Боковая панель «Что будет после отправки». */
function NextSteps() {
  return (
    <Panel
      tone="soft"
      gap={14}
      px={24}
      py={24}
      sx={{ width: 360, flexShrink: 0 }}
      data-testid="request-form-next-steps"
    >
      <PanelTitle>{t.nextTitle}</PanelTitle>
      {t.nextSteps.map((step, index) => (
        <Box key={step} sx={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
          <Box
            aria-hidden
            sx={{
              ...textStyles.label,
              width: 26,
              height: 26,
              flexShrink: 0,
              borderRadius: `${radius.full}px`,
              bgcolor: c('bg-surface'),
              color: c('action-primary-text'),
              display: 'grid',
              placeItems: 'center',
            }}
          >
            {index + 1}
          </Box>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {step}
          </Typography>
        </Box>
      ))}
    </Panel>
  );
}
