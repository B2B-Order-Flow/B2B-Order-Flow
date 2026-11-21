import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { queryKeys, requestsApi, usersApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import type { RequestView, StageView } from '../../api/types';
import { BackLink } from '../../components/ui/BackLink';
import { IntegrationErrorBadge, StatusBadge } from '../../components/StatusBadge';
import { SelectField } from '../../components/ui/FormField';
import { KeyValueGrid } from '../../components/ui/Panel';
import { Notice } from '../../components/ui/Notice';
import { formatSpeed } from '../../lib/format';
import { contractTypeOf, requestTitle } from '../../lib/requestView';
import { canCreateRequest, isCompanyRole } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { managerLabel, submittedLabel } from './labels';
import { useRequestAction } from './useRequestAction';
import { WorkflowActions } from './WorkflowActions';

const t = texts.requestCard;
const TERMINAL = new Set(['CLOSED', 'REJECTED', 'CANCELLED', 'DRAFT']);

// Шапка карточки: «Все заявки», номер и статус, адрес, сведения и действия активной роли.
export function RequestHeader({ request, myStages }: { request: RequestView; myStages: StageView[] }) {
  const { user, activeRole } = useSession();
  const navigate = useNavigate();
  const client = isCompanyRole(activeRole);
  const [assignOpen, setAssignOpen] = useState(false);
  const [offerOpen, setOfferOpen] = useState(false);

  const recheck = useRequestAction(
    request.id,
    () => requestsApi.recheckData(request.id, request.version),
    t.recheckStarted,
  );

  const meta = client
    ? [submittedLabel(request)]
    : [request.companyName, managerLabel(request, user), submittedLabel(request)].filter(Boolean);

  const coordinator = activeRole === 'COORDINATOR';
  const actions = (
    <>
      {coordinator && !TERMINAL.has(request.status) ? (
        <Button variant="outlined" onClick={() => setAssignOpen(true)} data-testid="action-assign-manager">
          {request.managerId ? t.reassignManager : t.assignManager}
        </Button>
      ) : null}
      {coordinator && request.integrationError ? (
        <Button
          variant="contained"
          loading={recheck.isPending}
          onClick={() => recheck.mutate(undefined)}
          data-testid="action-recheck-data"
        >
          {t.recheck}
        </Button>
      ) : null}
      {client && request.status === 'DRAFT' && canCreateRequest(activeRole) ? (
        <Button variant="contained" onClick={() => void navigate(`/requests/${request.id}/edit`)}>
          {t.continueDraft}
        </Button>
      ) : null}
      {client &&
      request.status === 'CONTRACT_SIGNING' &&
      contractTypeOf(request) === 'OFFER' &&
      canCreateRequest(activeRole) ? (
        <Button variant="contained" onClick={() => setOfferOpen(true)} data-testid="action-accept-offer">
          {t.acceptOffer}
        </Button>
      ) : null}
      <WorkflowActions request={request} myStages={myStages} />
    </>
  );

  return (
    <>
      <BackLink to="/requests">{t.back}</BackLink>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '20px' }}>
        <Stack sx={{ gap: '10px', minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <Typography variant="subtitle1" sx={{ color: c('text-muted') }} data-testid="request-card-number">
              {request.number}
            </Typography>
            <StatusBadge status={request.status} testId="request-card-status" />
            {!client && request.integrationError ? <IntegrationErrorBadge /> : null}
          </Box>
          <Typography variant="h1" component="h1" data-testid="request-card-title">
            {requestTitle(request)}
          </Typography>
          <Box sx={{ display: 'flex', columnGap: '20px', rowGap: '4px', flexWrap: 'wrap' }}>
            {meta.map((item) => (
              <Typography key={item} variant="body1" sx={{ color: c('text-muted') }}>
                {item}
              </Typography>
            ))}
          </Box>
        </Stack>
        <Box sx={{ display: 'flex', gap: '10px', flexShrink: 0 }}>{actions}</Box>
      </Box>
      {assignOpen ? <AssignManagerDialog request={request} onClose={() => setAssignOpen(false)} /> : null}
      {offerOpen ? <AcceptOfferDialog request={request} onClose={() => setOfferOpen(false)} /> : null}
    </>
  );
}

/** «Назначить менеджера»: сотрудники с ролью SALES_MANAGER (GET /users?role=…, ui-requirements.md §6.9). */
export function AssignManagerDialog({ request, onClose }: { request: RequestView; onClose: () => void }) {
  const [managerId, setManagerId] = useState('');
  const users = useQuery({
    queryKey: queryKeys.usersByRole('SALES_MANAGER'),
    queryFn: () => usersApi.byRole('SALES_MANAGER'),
  });
  const assign = useRequestAction(
    request.id,
    () => requestsApi.assignManager(request.id, managerId, request.version),
    t.managerAssigned,
    { onSuccess: onClose },
  );
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t.assignManager}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '12px', pt: '4px' }}>
          {users.isError ? (
            <Notice tone="error">{errorMessage(users.error)}</Notice>
          ) : (
            <SelectField
              label={t.manager}
              required
              value={managerId}
              onChange={setManagerId}
              disabled={users.isPending}
              placeholder={users.isPending ? texts.common.loading : t.chooseManager}
              options={(users.data?.content ?? [])
                .filter((u) => u.active && u.id !== request.managerId)
                .map((u) => ({ value: u.id, label: `${u.fullName} (${u.login})` }))}
              testId="assign-manager-select"
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button
          variant="contained"
          disabled={!managerId}
          loading={assign.isPending}
          onClick={() => assign.mutate(undefined)}
        >
          {t.assign}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** «Принять условия» для оферты: итоговые условия и кнопка акцепта (ui-requirements.md §7.3). */
function AcceptOfferDialog({ request, onClose }: { request: RequestView; onClose: () => void }) {
  const accept = useRequestAction(
    request.id,
    () => requestsApi.acceptOffer(request.id, request.version),
    t.offerAccepted,
    {
      onSuccess: onClose,
    },
  );
  const offer = request.offers?.find((o) => o.id === request.selectedOfferId) ?? request.offers?.[0];
  const contract = contractTypeOf(request);
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t.offerTitle}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '16px', pt: '4px' }}>
          <KeyValueGrid
            items={[
              { label: t.address, value: request.address ?? '—' },
              { label: t.speed, value: formatSpeed(offer?.speedMbps ?? request.requestedSpeedMbps) },
              { label: t.tariff, value: offer ? texts.tariffType[offer.tariffType] : '—' },
              { label: t.contract, value: contract ? texts.contractType[contract] : '—' },
            ]}
          />
          <Typography variant="caption" sx={{ color: c('text-muted') }}>
            {t.offerHint}
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button
          variant="contained"
          loading={accept.isPending}
          onClick={() => accept.mutate(undefined)}
          data-testid="action-accept-offer-confirm"
        >
          {t.acceptOfferSubmit}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
