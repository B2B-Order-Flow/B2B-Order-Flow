import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { requestsApi } from '../../api/endpoints';
import { fieldErrors } from '../../api/errors';
import type { ContractType, RequestView, TariffType } from '../../api/types';
import { SelectField, TextField } from '../../components/ui/FormField';
import { OptionCards } from '../../components/ui/OptionCards';
import { Panel, PanelTitle } from '../../components/ui/Panel';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { useRequestAction } from './useRequestAction';

const t = texts.commercialTerms;

/** Проценты строкой с двумя знаками (api-contract.md §10.1): «10» → «10.00». null — неверное значение. */
export function normalizePercent(value: string): string | null {
  const normalized = value.trim().replace(',', '.');
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(normalized)) return null;
  const number = Number(normalized);
  return number >= 0 && number <= 100 ? number.toFixed(2) : null;
}

// Коммерческие условия и отправка в работу — менеджер, статус «У менеджера», T04
// (ui-requirements.md §7.3, макет «UI-08 Коммерческие условия»).
export function CommercialTermsForm({ request }: { request: RequestView }) {
  const [tariffType, setTariffType] = useState<TariffType>('STANDARD');
  const [discount, setDiscount] = useState('');
  const [contractType, setContractType] = useState<ContractType>(request.contractPreference ?? 'OFFER');
  const [discountError, setDiscountError] = useState<string>();

  const submit = useRequestAction(
    request.id,
    (discountPercent: string | undefined) =>
      requestsApi.commercialTerms(request.id, { tariffType, contractType, discountPercent, version: request.version }),
    t.sent,
  );
  const serverErrors = fieldErrors(submit.error);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    let discountPercent: string | undefined;
    if (tariffType === 'INDIVIDUAL') {
      const normalized = discount.trim() ? normalizePercent(discount) : null;
      if (!normalized) {
        setDiscountError(discount.trim() ? t.discountInvalid : texts.common.required);
        return;
      }
      discountPercent = normalized;
    }
    setDiscountError(undefined);
    submit.mutate(discountPercent);
  };

  const wish = request.contractPreference ? texts.contractType[request.contractPreference] : null;

  return (
    <Panel component="form" noValidate onSubmit={onSubmit} gap={16} sx={{ flex: 1 }} data-testid="commercial-terms">
      <PanelTitle>{t.title}</PanelTitle>
      <Typography variant="body1" sx={{ color: c('text-muted') }}>
        {t.intro}
      </Typography>
      <Typography variant="subtitle1" component="div" id="tariff-label">
        {t.tariff} *
      </Typography>
      <OptionCards<TariffType>
        name="tariffType"
        label={t.tariff}
        direction="row"
        value={tariffType}
        onChange={setTariffType}
        testId="terms-tariff"
        options={[
          { value: 'STANDARD', label: texts.tariffType.STANDARD, hint: t.standardHint },
          { value: 'INDIVIDUAL', label: texts.tariffType.INDIVIDUAL, hint: t.individualHint },
        ]}
      />
      {tariffType === 'INDIVIDUAL' ? (
        <Box sx={{ maxWidth: 240 }}>
          <TextField
            label={t.discount}
            required
            value={discount}
            onChange={setDiscount}
            inputMode="decimal"
            error={discountError ?? serverErrors.discountPercent}
            testId="terms-discount"
          />
        </Box>
      ) : null}
      <SelectField
        label={t.contract}
        required
        value={contractType}
        onChange={(value) => setContractType(value as ContractType)}
        options={(['OFFER', 'STANDARD', 'CLIENT_FORM'] as const).map((value) => ({
          value,
          label: texts.contractType[value],
        }))}
        error={serverErrors.contractType}
        hint={`${wish ? t.clientWish(wish) : t.noWish} ${t.contractHint}`}
        testId="terms-contract"
      />
      <Box>
        <Button type="submit" variant="contained" loading={submit.isPending} data-testid="action-commercial-terms">
          {t.submit}
        </Button>
      </Box>
    </Panel>
  );
}
