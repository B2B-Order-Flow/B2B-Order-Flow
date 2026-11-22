import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useReducer, useState } from 'react';
import { queryKeys, requestsApi, stagesApi } from '../../api/endpoints';
import { fieldErrors, isApiError } from '../../api/errors';
import type { RequestView, StageType, StageView } from '../../api/types';
import { useNotify } from '../../components/Notifier';
import { TextField } from '../../components/ui/FormField';
import { Notice } from '../../components/ui/Notice';
import { todayIso } from '../../lib/format';
import { contractTypeOf } from '../../lib/requestView';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { dateAt } from './labels';
import { useRequestAction } from './useRequestAction';

const t = texts.workflow;

// Этапы, которые инженер ведёт из карточки на неделе 2. Стройка и устранение замечаний — неделя 3
const ENGINEER_STAGES = new Set<StageType>(['INSTALLATION', 'ACTIVATION']);

/** Менеджер этой заявки: договор и акт отмечает только он (core: requireOwnerManager). */
function useIsRequestManager(request: RequestView): boolean {
  const { user, activeRole } = useSession();
  return activeRole === 'SALES_MANAGER' && Boolean(user) && request.managerId === user?.id;
}

/** Срок приёмки из GET /requests/{id}/sla. null — срок ещё не загружен или неизвестен. */
function useAcceptanceDue(request: RequestView, enabled: boolean): number | null {
  const query = useQuery({
    queryKey: queryKeys.sla(request.id),
    queryFn: () => requestsApi.sla(request.id),
    enabled,
    retry: false,
  });
  const due = query.data?.timers.find((timer) => timer.kind === 'ACCEPTANCE' && timer.status !== 'STOPPED')?.dueAt;
  const ms = due ? Date.parse(due) : NaN;
  return Number.isNaN(ms) ? null : ms;
}

// Кнопки действий SC-01 в шапке карточки (ui-requirements.md §7.3 и §8). Видимость — по роли и статусу,
// окончательную проверку делает ядро.
export function WorkflowActions({ request }: { request: RequestView }) {
  const { activeRole } = useSession();
  const manager = useIsRequestManager(request);

  if (manager && request.status === 'CONTRACT_SIGNING' && contractTypeOf(request) !== 'OFFER') {
    return <ContractActions request={request} />;
  }
  if (manager && request.status === 'ACTIVATED') return <ActActions request={request} />;
  if (activeRole === 'ENGINEER' && request.status === 'IN_EXECUTION') {
    return <EngineerActions request={request} />;
  }
  return null;
}

/** Строка о том, чего сейчас ждёт менеджер: подпись, акцепт оферты или срок приёмки. */
export function WorkflowNotice({ request }: { request: RequestView }) {
  const manager = useIsRequestManager(request);
  if (!manager) return null;

  let text: string | null = null;
  if (request.status === 'CONTRACT_SIGNING') {
    if (contractTypeOf(request) === 'OFFER') text = t.waitingOffer;
    else if (request.contractSentAt) text = t.waitingSignature(dateAt(request.contractSentAt));
  } else if (request.status === 'ACTIVATED' && request.actSentAt) {
    text = t.waitingAct(dateAt(request.actSentAt));
  }
  if (!text) return null;
  return (
    <Notice tone="waiting" testId="request-card-workflow-notice">
      {text}
    </Notice>
  );
}

function ContractActions({ request }: { request: RequestView }) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const sent = useRequestAction(
    request.id,
    () => requestsApi.contractSent(request.id, request.version),
    t.contractSentDone,
  );
  const alreadySent = Boolean(request.contractSentAt);

  return (
    <>
      {alreadySent ? null : (
        <Button
          variant="outlined"
          loading={sent.isPending}
          onClick={() => sent.mutate(undefined)}
          data-testid="action-contract-sent"
        >
          {t.contractSent}
        </Button>
      )}
      <Button variant="contained" onClick={() => setDialogOpen(true)} data-testid="action-contract-concluded">
        {t.contractConcluded}
      </Button>
      {dialogOpen ? <ContractDialog request={request} onClose={() => setDialogOpen(false)} /> : null}
    </>
  );
}

/** «Договор заключён»: номер и дата подписания, T13. */
function ContractDialog({ request, onClose }: { request: RequestView; onClose: () => void }) {
  const [number, setNumber] = useState('');
  const [date, setDate] = useState(() => todayIso());
  const [errors, setErrors] = useState<{ contractNumber?: string; concludedAt?: string }>({});
  const conclude = useRequestAction(
    request.id,
    () =>
      requestsApi.contractConcluded(request.id, {
        contractNumber: number.trim(),
        concludedAt: date,
        version: request.version,
      }),
    t.contractConcludedDone,
    {
      onSuccess: onClose,
      onError: (error) => {
        setErrors(fieldErrors(error));
        return false;
      },
    },
  );

  const submit = () => {
    const next: typeof errors = {};
    if (!number.trim()) next.contractNumber = texts.common.required;
    else if (number.trim().length > 100) next.contractNumber = t.contractNumberTooLong;
    if (!date) next.concludedAt = texts.common.required;
    else if (date > todayIso()) next.concludedAt = t.contractDateFuture;
    setErrors(next);
    if (Object.keys(next).length === 0) conclude.mutate(undefined);
  };

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t.contractDialogTitle}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '16px', pt: '4px' }}>
          <TextField
            label={t.contractNumber}
            required
            value={number}
            onChange={setNumber}
            maxLength={100}
            autoFocus
            error={errors.contractNumber}
            testId="contract-number"
          />
          <TextField
            label={t.contractDate}
            required
            type="date"
            value={date}
            onChange={setDate}
            error={errors.concludedAt}
            testId="contract-date"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button
          variant="contained"
          loading={conclude.isPending}
          onClick={submit}
          data-testid="action-contract-concluded-confirm"
        >
          {t.contractConcluded}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function EngineerActions({ request }: { request: RequestView }) {
  const { user } = useSession();
  // Этап ищем в маршруте самой заявки: список «Моих задач» страничный, нужного этапа на странице может не быть
  const mine = request.stages?.find(
    (s) => s.status === 'IN_PROGRESS' && s.assigneeId === user?.id && ENGINEER_STAGES.has(s.type),
  );
  if (mine?.type === 'INSTALLATION') return <CompleteInstallation request={request} stage={mine} />;
  if (mine?.type === 'ACTIVATION') return <ActivateService request={request} stage={mine} />;

  // После монтажа следующий этап лежит в очереди: взять его можно прямо из карточки
  const queued = request.stages?.find(
    (s) => s.status === 'QUEUED' && s.queueRole === 'ENGINEER' && !s.assigneeId && ENGINEER_STAGES.has(s.type),
  );
  return queued ? <ClaimStage request={request} stage={queued} /> : null;
}

function ClaimStage({ request, stage }: { request: RequestView; stage: StageView }) {
  const notify = useNotify();
  const claim = useRequestAction(request.id, () => stagesApi.claim(stage.id, stage.version), t.claimed, {
    onError: (error) => {
      const taken =
        isApiError(error) && ['VERSION_CONFLICT', 'ILLEGAL_TRANSITION', 'STAGE_ALREADY_DECIDED'].includes(error.code);
      if (taken) notify.info(t.alreadyTaken);
      return taken;
    },
  });
  return (
    <Button
      variant="contained"
      loading={claim.isPending}
      onClick={() => claim.mutate(undefined)}
      data-testid="action-claim"
    >
      {t.claimStage(texts.stageType[stage.type])}
    </Button>
  );
}

function CompleteInstallation({ request, stage }: { request: RequestView; stage: StageView }) {
  const complete = useRequestAction(
    request.id,
    () => stagesApi.complete(stage.id, stage.version),
    t.installationDoneToast,
  );
  return (
    <Button
      variant="contained"
      loading={complete.isPending}
      onClick={() => complete.mutate(undefined)}
      data-testid="action-complete"
    >
      {t.installationDone}
    </Button>
  );
}

function ActivateService({ request, stage }: { request: RequestView; stage: StageView }) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const activate = useRequestAction(request.id, () => stagesApi.activate(stage.id, stage.version), t.activated, {
    onSuccess: () => setConfirmOpen(false),
  });
  return (
    <>
      <Button variant="contained" onClick={() => setConfirmOpen(true)} data-testid="action-activate">
        {t.activate}
      </Button>
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t.activateTitle}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {t.activateText}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="text" onClick={() => setConfirmOpen(false)}>
            {texts.common.cancel}
          </Button>
          <Button
            variant="contained"
            loading={activate.isPending}
            onClick={() => activate.mutate(undefined)}
            data-testid="action-activate-confirm"
          >
            {t.activateConfirm}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function ActActions({ request }: { request: RequestView }) {
  const notify = useNotify();
  const actSent = Boolean(request.actSentAt);
  const due = useAcceptanceDue(request, actSent);
  const [, rerender] = useReducer((tick: number) => tick + 1, 0);

  // Когда срок приёмки истекает, кнопка становится доступной без перезагрузки (в DEMO это 3 минуты)
  useEffect(() => {
    if (due === null) return undefined;
    const left = due - Date.now();
    if (left <= 0) return undefined;
    const id = window.setTimeout(rerender, Math.min(left + 500, 2_147_483_647));
    return () => window.clearTimeout(id);
  }, [due]);

  const send = useRequestAction(request.id, () => requestsApi.actSent(request.id, request.version), t.actSentDone);
  const sign = useRequestAction(request.id, () => requestsApi.actSigned(request.id, request.version), t.actSignedDone);
  const close = useRequestAction(
    request.id,
    () => requestsApi.closeByDefault(request.id, request.version),
    t.closedByDefault,
    {
      onError: (error) => {
        // Часы браузера и ядра могут расходиться: на 409 общий текст про смену статуса был бы неверен
        const early = isApiError(error) && error.code === 'ILLEGAL_TRANSITION';
        if (early) notify.info(t.acceptanceNotExpired);
        return early;
      },
    },
  );

  if (!actSent) {
    return (
      <Button
        variant="contained"
        loading={send.isPending}
        onClick={() => send.mutate(undefined)}
        data-testid="action-act-sent"
      >
        {t.actSent}
      </Button>
    );
  }

  // Закрыть по умолчанию можно, только когда срок приёмки известен и наступил (ui-requirements.md §7.3).
  // «Акт подписан» от загрузки срока не зависит
  const dueReached = due !== null && Date.now() >= due;
  const closeHint =
    due === null ? t.closeDueUnknown : dueReached ? '' : t.closeAfter(dateAt(new Date(due).toISOString()));
  const busy = sign.isPending || close.isPending;
  return (
    <>
      <Tooltip title={closeHint}>
        <span>
          <Button
            variant="outlined"
            disabled={!dueReached || busy}
            loading={close.isPending}
            onClick={() => close.mutate(undefined)}
            data-testid="action-close-by-default"
          >
            {t.closeByDefault}
          </Button>
        </span>
      </Tooltip>
      <Button
        variant="contained"
        disabled={busy}
        loading={sign.isPending}
        onClick={() => sign.mutate(undefined)}
        data-testid="action-act-signed"
      >
        {t.actSigned}
      </Button>
    </>
  );
}
