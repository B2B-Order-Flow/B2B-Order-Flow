import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { RequestView, StageView } from '../../api/types';
import { ErrorState, LoadingState } from '../../components/states';
import { Columns, Page } from '../../components/ui/Panel';
import { useSession } from '../../session/SessionProvider';
import { approvalStageFor } from '../../lib/approvals';
import { ApprovalDecisionForm, ApprovalTermsPanel } from './ApprovalDecisionForm';
import { ClientRequestPanel, ExternalDataPanel, StageSlaPanel, AddressPanel } from './InfoPanels';
import { CommercialTermsForm } from './CommercialTermsForm';
import { RequestHeader } from './RequestHeader';
import { RequestOverview } from './RequestOverview';
import { RequestSlaPanel } from './SlaPanel';
import { TechResultForm } from './TechResultForm';
import { WorkflowNotice } from './WorkflowActions';

const TECH_STAGES = new Set<StageView['type']>(['TECH_CHECK', 'RECHECK_TECH']);

// UI-08. Карточка заявки (ui-requirements.md §6.8, §7.3). Раскладка зависит от того, что ждёт
// от активной роли: менеджер фиксирует коммерческие условия, техспециалист вносит результат ТВ,
// остальные видят обзор (макеты «UI-08 Карточка заявки (EC-10)», «Коммерческие условия», «Результат проверки ТВ»).
export function RequestCardPage() {
  const { requestId = '' } = useParams();
  const { user, activeRole } = useSession();

  const query = useQuery({ queryKey: queryKeys.request(requestId), queryFn: () => requestsApi.get(requestId) });

  if (query.isPending) return <LoadingState />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;

  const request = query.data;
  // Этап ищем в маршруте самой заявки: список «Моих задач» страничный, нужного этапа на странице может не быть
  const techStage =
    activeRole === 'TECH_SPECIALIST' && request.status === 'TECH_CHECK'
      ? request.stages?.find((s) => TECH_STAGES.has(s.type) && s.status === 'IN_PROGRESS' && s.assigneeId === user?.id)
      : undefined;
  // Согласующий с этапом в работе решает прямо в карточке (неделя 3, ui-requirements.md §9.1)
  const approvalStage = techStage ? undefined : approvalStageFor(request, activeRole, user?.id, 'IN_PROGRESS');
  const termsMode =
    activeRole === 'SALES_MANAGER' && request.status === 'WITH_MANAGER' && request.managerId === user?.id;

  return (
    <Page gap={22} top={28} testId="request-card">
      <RequestHeader request={request} />
      <WorkflowNotice request={request} />
      {termsMode ? (
        <TermsLayout request={request} />
      ) : techStage ? (
        <TechLayout request={request} stage={techStage} />
      ) : approvalStage ? (
        <ApprovalLayout request={request} stage={approvalStage} />
      ) : (
        <RequestOverview request={request} />
      )}
    </Page>
  );
}

function TermsLayout({ request }: { request: RequestView }) {
  return (
    <>
      <Columns>
        <CommercialTermsForm request={request} />
        <Columns sx={{ flex: 1, flexDirection: 'column', minWidth: 0, '& > *': { width: '100%' } }}>
          <ClientRequestPanel request={request} />
          <ExternalDataPanel request={request} />
        </Columns>
      </Columns>
      <RequestSlaPanel request={request} />
    </>
  );
}

function TechLayout({ request, stage }: { request: RequestView; stage: StageView }) {
  return (
    <Columns>
      <TechResultForm request={request} stage={stage} />
      <Columns sx={{ flex: 1, flexDirection: 'column', minWidth: 0, '& > *': { width: '100%' } }}>
        <StageSlaPanel stage={stage} />
        <AddressPanel request={request} />
      </Columns>
    </Columns>
  );
}

function ApprovalLayout({ request, stage }: { request: RequestView; stage: StageView }) {
  return (
    <>
      <Columns>
        <ApprovalDecisionForm request={request} stage={stage} />
        <ApprovalTermsPanel request={request} stage={stage} />
      </Columns>
      <RequestOverview request={request} />
    </>
  );
}
