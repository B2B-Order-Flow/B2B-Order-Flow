import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { RequestView, StageView } from '../../api/types';
import { KeyValueGrid, Panel, PanelTitle } from '../../components/ui/Panel';
import { formatDate, formatMoney } from '../../lib/format';
import { stageTimer } from '../../lib/sla';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { speedLabel } from './labels';
import { SlaTimerRow } from './SlaPanel';

const t = texts.requestCard;

/** «Заявка клиента» в раскладке менеджера. */
export function ClientRequestPanel({ request }: { request: RequestView }) {
  return (
    <Panel gap={14}>
      <PanelTitle>{t.clientRequest}</PanelTitle>
      <KeyValueGrid
        items={[
          { label: t.speed, value: speedLabel(request) },
          { label: t.desiredDate, value: formatDate(request.desiredDate) },
          { label: t.contact, value: request.contactName ?? '—' },
          {
            label: t.contractWish,
            value: request.contractPreference ? texts.contractType[request.contractPreference] : t.notSpecified,
          },
          ...(request.clientComment ? [{ label: t.comment, value: request.clientComment }] : []),
        ]}
      />
    </Panel>
  );
}

/** «Данные внешних систем»: сегмент (CRM), задолженность (биллинг), госзаказчик (справочник). */
export function ExternalDataPanel({ request }: { request: RequestView }) {
  const debt = Number(request.debtAmount ?? 0);
  return (
    <Panel gap={14} data-testid="external-data">
      <PanelTitle>{t.externalData}</PanelTitle>
      <KeyValueGrid
        items={[
          {
            label: t.segment,
            value: request.segment ? (texts.segment[request.segment] ?? request.segment) : t.notInCrm,
          },
          { label: t.debt, value: debt > 0 ? formatMoney(request.debtAmount) : texts.common.no },
          { label: t.govCustomer, value: request.isGovCustomer ? texts.common.yes : texts.common.no },
          { label: t.crmManager, value: request.crmManagerId ? t.crmManagerSet : t.crmManagerNone },
        ]}
      />
    </Panel>
  );
}

/** «Срок этапа»: индикатор и два времени — по SLA и календарное (sla.md §10). Данные — GET /requests/{id}/sla. */
export function StageSlaPanel({ stage }: { stage: StageView }) {
  const query = useQuery({ queryKey: queryKeys.sla(stage.requestId), queryFn: () => requestsApi.sla(stage.requestId) });
  const timer = stageTimer(query.data?.timers, stage.id);
  return (
    <Panel gap={12} data-testid="stage-sla">
      <PanelTitle>{t.stageSla}</PanelTitle>
      {query.isPending ? (
        <Typography variant="body1" sx={{ color: c('text-muted') }}>
          {texts.common.loading}
        </Typography>
      ) : timer ? (
        <SlaTimerRow timer={timer} />
      ) : (
        <Typography variant="body1" sx={{ color: c('text-muted') }}>
          {t.slaUnknown}
        </Typography>
      )}
    </Panel>
  );
}

/** «Адрес и параметры» в раскладке техспециалиста. */
export function AddressPanel({ request }: { request: RequestView }) {
  return (
    <Panel gap={14}>
      <PanelTitle>{t.addressParams}</PanelTitle>
      <KeyValueGrid
        items={[
          { label: t.address, value: request.address ?? '—' },
          { label: t.speed, value: speedLabel(request) },
          { label: t.desiredDate, value: formatDate(request.desiredDate) },
        ]}
      />
    </Panel>
  );
}
