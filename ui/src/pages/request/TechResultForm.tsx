import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { stagesApi } from '../../api/endpoints';
import { fieldErrors, isApiError } from '../../api/errors';
import type { RequestView, StageView, TechResult, TechResultPayload } from '../../api/types';
import { FieldRow, SelectField, TextField } from '../../components/ui/FormField';
import { Notice } from '../../components/ui/Notice';
import { OptionCards } from '../../components/ui/OptionCards';
import { Panel, PanelTitle } from '../../components/ui/Panel';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { useRequestAction } from './useRequestAction';

const t = texts.techResult;

/** Деньги строкой с двумя знаками (api-contract.md §10.1): «1 200 000» → «1200000.00». */
export function normalizeMoney(value: string): string | null {
  const normalized = value.replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  return Number(normalized).toFixed(2);
}

type Errors = Partial<Record<'result' | 'capex' | 'constructionDays' | 'reasonCode' | 'comment', string>>;

// Результат проверки ТВ — техспециалист, исполнитель этапа «Проверка ТВ», T05–T07
// (ui-requirements.md §7.3, макет «UI-08 Результат проверки ТВ»).
export function TechResultForm({ request, stage }: { request: RequestView; stage: StageView }) {
  const [result, setResult] = useState<TechResult | null>(null);
  const [capex, setCapex] = useState('');
  const [days, setDays] = useState('');
  const [reasonCode, setReasonCode] = useState('');
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [confirmNone, setConfirmNone] = useState(false);
  // «Есть со стройкой» ядро примет с недели 3 (T06): до тех пор — 409 ILLEGAL_TRANSITION
  const [constructionUnavailable, setConstructionUnavailable] = useState(false);

  const save = useRequestAction(
    request.id,
    (payload: TechResultPayload) => stagesApi.techResult(stage.id, payload),
    t.saved,
    {
      onSuccess: () => setConfirmNone(false),
      onError: (error, payload) => {
        const blocked =
          payload.result === 'WITH_CONSTRUCTION' && isApiError(error) && error.code === 'ILLEGAL_TRANSITION';
        setConstructionUnavailable(blocked);
        return blocked;
      },
    },
  );
  const serverErrors = fieldErrors(save.error) as Errors;

  const buildPayload = (): TechResultPayload | null => {
    const next: Errors = {};
    if (!result) next.result = t.chooseResult;
    const payload: TechResultPayload = { result: result ?? 'AVAILABLE', version: stage.version };
    if (result === 'WITH_CONSTRUCTION') {
      const money = normalizeMoney(capex);
      if (!capex.trim()) next.capex = texts.common.required;
      else if (!money) next.capex = t.capexInvalid;
      else payload.capex = money;
      if (!days.trim()) next.constructionDays = texts.common.required;
      else if (!/^\d+$/.test(days.trim()) || Number(days) < 1) next.constructionDays = t.daysInvalid;
      else payload.constructionDays = Number(days);
    }
    if (result === 'NONE') {
      if (!reasonCode) next.reasonCode = texts.common.required;
      else payload.reasonCode = reasonCode;
      if (reasonCode === 'OTHER' && !comment.trim()) next.comment = texts.common.required;
      if (comment.trim()) payload.comment = comment.trim();
    }
    setErrors(next);
    return Object.keys(next).length === 0 ? payload : null;
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const payload = buildPayload();
    if (!payload) return;
    // «Нет» отклоняет заявку — сначала подтверждение (EC-01)
    if (payload.result === 'NONE') setConfirmNone(true);
    else save.mutate(payload);
  };

  return (
    <Panel component="form" noValidate onSubmit={onSubmit} gap={14} sx={{ flex: 1 }} data-testid="tech-result">
      <PanelTitle>{t.title}</PanelTitle>
      <OptionCards<TechResult>
        name="techResult"
        label={t.title}
        value={result}
        onChange={(value) => {
          setResult(value);
          setErrors({});
          setConstructionUnavailable(false);
        }}
        testId="tech-result-option"
        options={[
          { value: 'AVAILABLE', label: t.available, hint: t.availableHint },
          { value: 'WITH_CONSTRUCTION', label: t.construction, hint: t.constructionHint },
          { value: 'NONE', label: t.none, hint: t.noneHint },
        ]}
      />
      {errors.result ? (
        <Typography variant="caption" role="alert" sx={{ color: c('sla-breach-text') }}>
          {errors.result}
        </Typography>
      ) : null}
      {result === 'WITH_CONSTRUCTION' ? (
        <FieldRow>
          <TextField
            label={t.capex}
            required
            value={capex}
            onChange={setCapex}
            inputMode="decimal"
            error={errors.capex ?? serverErrors.capex}
            testId="tech-capex"
          />
          <TextField
            label={t.days}
            required
            value={days}
            onChange={setDays}
            inputMode="numeric"
            error={errors.constructionDays ?? serverErrors.constructionDays}
            testId="tech-days"
          />
        </FieldRow>
      ) : null}
      {result === 'WITH_CONSTRUCTION' && constructionUnavailable ? (
        <Notice tone="warning" title={t.constructionUnavailableTitle} testId="tech-construction-unavailable">
          {t.constructionUnavailable}
        </Notice>
      ) : null}
      {result === 'NONE' ? (
        <>
          <SelectField
            label={t.reason}
            required
            value={reasonCode}
            onChange={setReasonCode}
            placeholder={t.chooseReason}
            options={Object.entries(t.reasons).map(([value, label]) => ({ value, label }))}
            error={errors.reasonCode ?? serverErrors.reasonCode}
            testId="tech-reason"
          />
          <TextField
            label={t.comment}
            required={reasonCode === 'OTHER'}
            value={comment}
            onChange={setComment}
            rows={3}
            maxLength={4000}
            error={errors.comment ?? serverErrors.comment}
            testId="tech-comment"
          />
        </>
      ) : null}
      <Box sx={{ display: 'flex', gap: '12px', pt: '4px' }}>
        <Button type="submit" variant="contained" loading={save.isPending && !confirmNone} data-testid="tech-submit">
          {t.submit}
        </Button>
      </Box>

      <Dialog open={confirmNone} onClose={() => setConfirmNone(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t.noneConfirmTitle}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {t.noneConfirmText}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="text" onClick={() => setConfirmNone(false)}>
            {texts.common.cancel}
          </Button>
          <Button
            variant="contained"
            loading={save.isPending}
            onClick={() => {
              const payload = buildPayload();
              if (payload) save.mutate(payload);
            }}
            data-testid="tech-none-confirm"
          >
            {t.noneConfirm}
          </Button>
        </DialogActions>
      </Dialog>
    </Panel>
  );
}
