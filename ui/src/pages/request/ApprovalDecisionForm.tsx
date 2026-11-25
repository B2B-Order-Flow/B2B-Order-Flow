import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { stagesApi } from '../../api/endpoints';
import { fieldErrors } from '../../api/errors';
import type { ApprovalDecision, RequestView, StageDecisionPayload, StageView } from '../../api/types';
import { SelectField, TextField } from '../../components/ui/FormField';
import { KeyValueGrid, Panel, PanelTitle } from '../../components/ui/Panel';
import { OptionCards } from '../../components/ui/OptionCards';
import { formatMoney } from '../../lib/format';
import { contractTypeOf } from '../../lib/requestView';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { speedLabel } from './labels';
import { useRejectionReasons } from './useRejectionReasons';
import { useRequestAction } from './useRequestAction';

const t = texts.approval;

type Errors = Partial<Record<'decision' | 'comment' | 'reasonCode', string>>;

// Решение согласующего: согласовать / вернуть на доработку / отклонить (status-model.md T08, T09, T11;
// ui-requirements.md §9.1). Комментарий обязателен для возврата и отклонения, причина — для отклонения.
export function ApprovalDecisionForm({ request, stage }: { request: RequestView; stage: StageView }) {
  const [decision, setDecision] = useState<ApprovalDecision | null>(null);
  const [comment, setComment] = useState('');
  const [reasonCode, setReasonCode] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [confirmReject, setConfirmReject] = useState(false);
  const reasons = useRejectionReasons(stage.type);

  const decide = useRequestAction(
    request.id,
    (payload: StageDecisionPayload) => stagesApi.decide(stage.id, payload),
    t.done[decision ?? 'APPROVE'],
    { onSuccess: () => setConfirmReject(false) },
  );
  const serverErrors = fieldErrors(decide.error) as Errors;

  const buildPayload = (): StageDecisionPayload | null => {
    const next: Errors = {};
    if (!decision) next.decision = t.choose;
    if ((decision === 'RETURN' || decision === 'REJECT') && !comment.trim()) next.comment = texts.common.required;
    if (decision === 'REJECT' && !reasonCode) next.reasonCode = texts.common.required;
    setErrors(next);
    if (Object.keys(next).length > 0 || !decision) return null;
    return {
      decision,
      version: stage.version,
      ...(comment.trim() ? { comment: comment.trim() } : {}),
      ...(decision === 'REJECT' ? { reasonCode } : {}),
    };
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const payload = buildPayload();
    if (!payload) return;
    // Отклонение завершает заявку целиком (EC-05) — сначала подтверждение
    if (payload.decision === 'REJECT') setConfirmReject(true);
    else decide.mutate(payload);
  };

  const commentRequired = decision === 'RETURN' || decision === 'REJECT';

  return (
    <Panel component="form" noValidate onSubmit={onSubmit} gap={14} sx={{ flex: 1 }} data-testid="approval-decision">
      <PanelTitle>{t.title}</PanelTitle>
      <Typography variant="body1" sx={{ color: c('text-secondary') }}>
        {t.intro(texts.stageType[stage.type])}
      </Typography>
      <OptionCards<ApprovalDecision>
        name="approvalDecision"
        label={t.title}
        value={decision}
        onChange={(value) => {
          setDecision(value);
          setErrors({});
        }}
        testId="decision-option"
        options={(['APPROVE', 'RETURN', 'REJECT'] as const).map((value) => ({
          value,
          label: t.decisions[value],
          hint: t.hints[value],
        }))}
      />
      {errors.decision ? (
        <Typography variant="caption" role="alert" sx={{ color: c('sla-breach-text') }}>
          {errors.decision}
        </Typography>
      ) : null}
      {decision === 'REJECT' ? (
        <SelectField
          label={t.reason}
          required
          value={reasonCode}
          onChange={setReasonCode}
          disabled={reasons.loading}
          placeholder={reasons.loading ? texts.common.loading : t.chooseReason}
          options={reasons.options}
          error={errors.reasonCode ?? serverErrors.reasonCode}
          testId="decision-reason"
        />
      ) : null}
      {decision ? (
        <TextField
          label={decision === 'RETURN' ? t.commentReturn : t.comment}
          required={commentRequired}
          value={comment}
          onChange={setComment}
          rows={3}
          maxLength={4000}
          error={errors.comment ?? serverErrors.comment}
          testId="decision-comment"
        />
      ) : null}
      <Box sx={{ display: 'flex', gap: '12px', pt: '4px' }}>
        <Button
          type="submit"
          variant="contained"
          loading={decide.isPending && !confirmReject}
          data-testid="action-decision"
        >
          {t.submit}
        </Button>
      </Box>

      <Dialog open={confirmReject} onClose={() => setConfirmReject(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t.rejectConfirmTitle}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {t.rejectConfirmText}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="text" onClick={() => setConfirmReject(false)}>
            {texts.common.cancel}
          </Button>
          <Button
            variant="contained"
            color="error"
            loading={decide.isPending}
            onClick={() => {
              const payload = buildPayload();
              if (payload) decide.mutate(payload);
            }}
            data-testid="action-decision-confirm"
          >
            {t.rejectConfirm}
          </Button>
        </DialogActions>
      </Dialog>
    </Panel>
  );
}

/** Что согласуют: условия выбранного варианта, данные внешних систем, результат ТВ и правила этапа. */
export function ApprovalTermsPanel({ request, stage }: { request: RequestView; stage: StageView }) {
  const offer = request.offers?.find((o) => o.id === request.selectedOfferId) ?? request.offers?.[0];
  const contract = contractTypeOf(request);
  const debt = Number(request.debtAmount ?? 0);
  const items = [
    { label: t.speed, value: speedLabel(request) },
    { label: t.tariff, value: offer ? texts.tariffType[offer.tariffType] : '—' },
    {
      label: t.discount,
      value: offer?.discountPercent ? `${offer.discountPercent.replace('.', ',')} %` : texts.common.no,
    },
    { label: t.contract, value: contract ? texts.contractType[contract] : '—' },
    { label: t.segment, value: request.segment ? (texts.segment[request.segment] ?? request.segment) : '—' },
    { label: t.debt, value: debt > 0 ? formatMoney(request.debtAmount) : texts.common.no },
    { label: t.govCustomer, value: request.isGovCustomer ? texts.common.yes : texts.common.no },
    ...(request.capex ? [{ label: t.capex, value: formatMoney(request.capex) }] : []),
    ...(request.constructionDays ? [{ label: t.constructionDays, value: t.days(request.constructionDays) }] : []),
    ...(stage.requiredByRules?.length ? [{ label: t.rules, value: stage.requiredByRules.join(', ') }] : []),
  ];
  return (
    <Panel gap={14} sx={{ flex: 1 }} data-testid="approval-terms">
      <PanelTitle>{t.terms}</PanelTitle>
      <KeyValueGrid items={items} />
    </Panel>
  );
}
