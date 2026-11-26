import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { queryKeys, requestsApi } from '../../api/endpoints';
import type { ContractType, RequestView, RevisionChange, TariffType } from '../../api/types';
import { Panel, PanelTitle } from '../../components/ui/Panel';
import { formatDate } from '../../lib/format';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { dateAt } from './labels';

const t = texts.rework;
const REVISIONS_QUERY = { page: 0, size: 20 };

/** Значение поля ревизии для человека: перечисления — подписями, дата — по-русски, пусто — «—». */
function valueLabel(field: string, value: string | undefined): string {
  if (value === undefined || value === null || value === '') return '—';
  switch (field) {
    case 'tariffType':
      return texts.tariffType[value as TariffType] ?? value;
    case 'contractType':
    case 'contractPreference':
      return texts.contractType[value as ContractType] ?? value;
    case 'desiredDate':
      return formatDate(value);
    case 'discountPercent':
      return `${value.replace('.', ',')} %`;
    case 'speedMbps':
    case 'requestedSpeedMbps':
      return `${value} Мбит/с`;
    default:
      return value;
  }
}

const fieldLabel = (change: RevisionChange) => t.fields[change.field] ?? change.field;

// Что изменилось между ревизиями (GET /requests/{id}/revisions). Ревизии видят менеджер заявки,
// координатор и руководитель B2B; другим ролям ядро отвечает 403, поэтому запрос им не отправляется.
// Первая ревизия — отправка заявки, её отличия «от пустого снимка» не показываются.
export function RevisionsPanel({ request }: { request: RequestView }) {
  const { user, activeRole } = useSession();
  const allowed =
    activeRole === 'COORDINATOR' ||
    activeRole === 'B2B_HEAD' ||
    (activeRole === 'SALES_MANAGER' && Boolean(user) && request.managerId === user?.id);
  const query = useQuery({
    queryKey: queryKeys.revisions(request.id),
    queryFn: () => requestsApi.revisions(request.id, REVISIONS_QUERY),
    enabled: allowed,
    retry: false,
  });
  const revisions = (query.data?.content ?? []).filter((r) => r.revisionNo > 1 && r.changes.length > 0);
  if (!allowed || revisions.length === 0) return null;

  return (
    <Panel gap={14} data-testid="request-card-revisions">
      <PanelTitle>{t.revisionsTitle}</PanelTitle>
      {revisions.map((revision) => (
        <Stack key={revision.id} sx={{ gap: '6px' }} data-testid={`revision-${revision.revisionNo}`}>
          <Typography variant="subtitle2" component="div">
            {t.revisionTitle(revision.revisionNo, t.reasons[revision.reason] ?? revision.reason)}
          </Typography>
          <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
            {dateAt(revision.createdAt, false)}
          </Typography>
          {revision.changes.map((change) => (
            <Box
              key={change.field}
              sx={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}
              data-testid="revision-change"
            >
              <Typography variant="body1" sx={{ color: c('text-secondary') }}>
                {fieldLabel(change)}:
              </Typography>
              <Typography variant="body1" sx={{ color: c('text-muted'), textDecoration: 'line-through' }}>
                {valueLabel(change.field, change.previousValue)}
              </Typography>
              <Typography variant="body1" sx={{ color: c('text-muted') }} aria-hidden>
                →
              </Typography>
              <Typography variant="subtitle2" component="span">
                {valueLabel(change.field, change.newValue)}
              </Typography>
            </Box>
          ))}
        </Stack>
      ))}
    </Panel>
  );
}
