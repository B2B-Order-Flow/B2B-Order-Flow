import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { queryKeys, requestsApi } from '../../api/endpoints';
import { fieldErrors } from '../../api/errors';
import type { ContractType, RequestView, TariffType, UpdateRequestDataPayload } from '../../api/types';
import { FieldRow, SelectField, TextField } from '../../components/ui/FormField';
import { Notice } from '../../components/ui/Notice';
import { OptionCards } from '../../components/ui/OptionCards';
import { Columns, Panel, PanelTitle } from '../../components/ui/Panel';
import { todayIso } from '../../lib/format';
import { isValidPhone } from '../../lib/requisites';
import { contractTypeOf } from '../../lib/requestView';
import { texts } from '../../texts/ru';
import { normalizePercent } from './CommercialTermsForm';
import { ClientRequestPanel, ExternalDataPanel } from './InfoPanels';
import { actorLabel } from './labels';
import { RequestOverview } from './RequestOverview';
import { useRequestAction } from './useRequestAction';
import { useSession } from '../../session/SessionProvider';

const t = texts.rework;
const f = texts.requestForm;

interface Form {
  address: string;
  desiredDate: string;
  contactName: string;
  contactPhone: string;
  speed: string;
  tariffType: TariffType;
  discount: string;
  contractType: ContractType;
}

type Errors = Partial<Record<keyof Form, string>>;

function initialForm(request: RequestView): Form {
  const offer = request.offers?.find((o) => o.id === request.selectedOfferId) ?? request.offers?.[0];
  return {
    address: request.address ?? '',
    desiredDate: request.desiredDate ?? '',
    contactName: request.contactName ?? '',
    contactPhone: request.contactPhone ?? '',
    speed: String(offer?.speedMbps ?? request.requestedSpeedMbps ?? ''),
    tariffType: offer?.tariffType ?? 'STANDARD',
    discount: offer?.discountPercent ? offer.discountPercent.replace('.', ',') : '',
    contractType: contractTypeOf(request) ?? 'OFFER',
  };
}

/** Проверки как в форме клиента (ui-requirements.md §6.7) и в коммерческих условиях (§7.3). */
function validate(form: Form): { errors: Errors; payload?: Omit<UpdateRequestDataPayload, 'version'> } {
  const errors: Errors = {};
  if (!form.address.trim()) errors.address = texts.common.required;
  else if (form.address.trim().length > 500) errors.address = f.errors.tooLong(500);
  const speed = form.speed.trim();
  if (!speed) errors.speed = texts.common.required;
  else if (!/^\d+$/.test(speed) || Number(speed) < 1 || Number(speed) > 100_000) errors.speed = f.errors.speed;
  if (form.desiredDate && form.desiredDate < todayIso()) errors.desiredDate = f.errors.date;
  if (form.contactPhone.trim() && !isValidPhone(form.contactPhone.trim())) errors.contactPhone = f.errors.phone;
  let discountPercent: string | undefined;
  if (form.tariffType === 'INDIVIDUAL') {
    const normalized = form.discount.trim() ? normalizePercent(form.discount) : null;
    if (!normalized)
      errors.discount = form.discount.trim() ? texts.commercialTerms.discountInvalid : texts.common.required;
    else discountPercent = normalized;
  }
  if (Object.keys(errors).length > 0) return { errors };
  return {
    errors,
    payload: {
      address: form.address.trim(),
      desiredDate: form.desiredDate || undefined,
      contactName: form.contactName.trim() || undefined,
      contactPhone: form.contactPhone.trim() || undefined,
      speedMbps: Number(speed),
      tariffType: form.tariffType,
      discountPercent: form.tariffType === 'INDIVIDUAL' ? discountPercent : undefined,
      contractType: form.contractType,
    },
  };
}

// Доработка заявки менеджером (T09, T09b, T12 → T10, ui-requirements.md §9.3): что исправить, правка данных
// и условий (PATCH /requests/{id}/data), повторная отправка (POST /requests/{id}/resubmit).
export function ReworkLayout({ request }: { request: RequestView }) {
  return (
    <>
      <ReturnReason request={request} />
      <Columns>
        <ReworkForm request={request} />
        <Columns sx={{ flex: 1, flexDirection: 'column', minWidth: 0, '& > *': { width: '100%' } }}>
          <ClientRequestPanel request={request} />
          <ExternalDataPanel request={request} />
        </Columns>
      </Columns>
      <RequestOverview request={request} />
    </>
  );
}

/** «Что исправить»: последний возврат на доработку из истории заявки — кто вернул и комментарий. */
function ReturnReason({ request }: { request: RequestView }) {
  const { user } = useSession();
  const query = { page: 0, size: 20, sort: 'createdAt,desc' };
  const history = useQuery({
    queryKey: queryKeys.history(request.id, query),
    queryFn: () => requestsApi.history(request.id, query),
  });
  const entry = history.data?.content.find((e) => e.toStatus === 'REWORK');
  if (history.isPending) return null;
  return (
    <Notice tone="warning" title={t.reasonTitle} testId="rework-reason">
      {entry?.comment ? `${entry.comment} — ${actorLabel(entry, user)}` : t.reasonUnknown}
    </Notice>
  );
}

function ReworkForm({ request }: { request: RequestView }) {
  const [form, setForm] = useState<Form>(() => initialForm(request));
  const [saved, setSaved] = useState<Form>(() => initialForm(request));
  const [errors, setErrors] = useState<Errors>({});
  const dirty = JSON.stringify(form) !== JSON.stringify(saved);
  const set =
    <K extends keyof Form>(key: K) =>
    (value: Form[K]) =>
      setForm((prev) => ({ ...prev, [key]: value }));

  const save = useRequestAction(
    request.id,
    (payload: Omit<UpdateRequestDataPayload, 'version'>) =>
      requestsApi.updateData(request.id, { ...payload, version: request.version }),
    t.saved,
    { onSuccess: () => setSaved(form) },
  );
  // Повторная отправка сначала сохраняет несохранённые изменения, затем отправляет (как форма клиента, §6.7)
  const resubmit = useRequestAction(
    request.id,
    async (payload: Omit<UpdateRequestDataPayload, 'version'> | null) => {
      const version = payload
        ? (await requestsApi.updateData(request.id, { ...payload, version: request.version })).version
        : request.version;
      return requestsApi.resubmit(request.id, version);
    },
    t.resubmitted,
  );
  const serverErrors = fieldErrors(save.error ?? resubmit.error) as Errors & Record<string, string>;

  const checked = () => {
    const result = validate(form);
    setErrors(result.errors);
    return result.payload;
  };
  const onSave = (event: FormEvent) => {
    event.preventDefault();
    const payload = checked();
    if (payload) save.mutate(payload);
  };
  const onResubmit = () => {
    const payload = checked();
    if (payload) resubmit.mutate(dirty ? payload : null);
  };
  const busy = save.isPending || resubmit.isPending;

  return (
    <Panel component="form" noValidate onSubmit={onSave} gap={16} sx={{ flex: 1 }} data-testid="rework-form">
      <PanelTitle>{t.title}</PanelTitle>
      <TextField
        label={f.address}
        required
        value={form.address}
        onChange={set('address')}
        maxLength={500}
        error={errors.address ?? serverErrors.address}
        testId="rework-address"
      />
      <FieldRow>
        <TextField
          label={t.speed}
          required
          value={form.speed}
          onChange={set('speed')}
          inputMode="numeric"
          error={errors.speed ?? serverErrors.speedMbps}
          testId="rework-speed"
        />
        <TextField
          label={f.desiredDate}
          type="date"
          value={form.desiredDate}
          onChange={set('desiredDate')}
          error={errors.desiredDate ?? serverErrors.desiredDate}
          testId="rework-date"
        />
      </FieldRow>
      <FieldRow>
        <TextField
          label={f.contactName}
          value={form.contactName}
          onChange={set('contactName')}
          maxLength={200}
          testId="rework-contact-name"
        />
        <TextField
          label={f.contactPhone}
          value={form.contactPhone}
          onChange={set('contactPhone')}
          placeholder={f.phonePlaceholder}
          error={errors.contactPhone ?? serverErrors.contactPhone}
          testId="rework-contact-phone"
        />
      </FieldRow>
      <OptionCards<TariffType>
        name="reworkTariff"
        label={texts.commercialTerms.tariff}
        value={form.tariffType}
        onChange={set('tariffType')}
        direction="row"
        testId="rework-tariff"
        options={[
          { value: 'STANDARD', label: texts.tariffType.STANDARD, hint: texts.commercialTerms.standardHint },
          { value: 'INDIVIDUAL', label: texts.tariffType.INDIVIDUAL, hint: texts.commercialTerms.individualHint },
        ]}
      />
      {form.tariffType === 'INDIVIDUAL' ? (
        <TextField
          label={texts.commercialTerms.discount}
          required
          value={form.discount}
          onChange={set('discount')}
          inputMode="decimal"
          error={errors.discount ?? serverErrors.discountPercent}
          testId="rework-discount"
        />
      ) : null}
      <SelectField
        label={texts.commercialTerms.contract}
        required
        value={form.contractType}
        onChange={(value) => set('contractType')(value as ContractType)}
        options={(['OFFER', 'STANDARD', 'CLIENT_FORM'] as const).map((value) => ({
          value,
          label: texts.contractType[value],
        }))}
        testId="rework-contract"
      />
      <Box sx={{ display: 'flex', gap: '12px', flexWrap: 'wrap', pt: '4px' }}>
        <Button
          variant="contained"
          loading={resubmit.isPending}
          disabled={busy}
          onClick={onResubmit}
          data-testid="action-resubmit"
        >
          {t.resubmit}
        </Button>
        <Button
          type="submit"
          variant="outlined"
          loading={save.isPending}
          disabled={busy || !dirty}
          data-testid="action-save-data"
        >
          {t.save}
        </Button>
      </Box>
    </Panel>
  );
}
