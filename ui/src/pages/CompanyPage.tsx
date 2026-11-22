import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { companiesApi, queryKeys } from '../api/endpoints';
import { CompanyStatusBadge } from '../components/StatusBadge';
import { Avatar } from '../components/ui/Brand';
import { Columns, KeyValueGrid, Muted, Page, PageTitle, Panel, PanelTitle } from '../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatDate } from '../lib/format';
import { isCompanyRole } from '../session/roles';
import { useSession } from '../session/SessionProvider';
import { texts } from '../texts/ru';
import { c } from '../theme/tokens';

const t = texts.company;

// UI-04. Моя компания (ui-requirements.md §6.4, макет «UI-04 Моя компания»).
// Операции участников компании (§9, П2) на неделях 1–2 не реализуются: блок «Сотрудники»
// показывает текущего пользователя, кнопка добавления недоступна (открытый вопрос 5).
export function CompanyPage() {
  const { user, company, roles } = useSession();
  const query = useQuery({
    queryKey: queryKeys.company(company?.id ?? ''),
    queryFn: () => companiesApi.get(company!.id),
    enabled: Boolean(company),
  });

  if (!company) return <EmptyState text={t.noCompany} />;
  if (query.isPending) return <LoadingState />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;

  const data = query.data;
  const myRoles = roles.filter(isCompanyRole).map((role) => texts.roles[role]);
  const subtitle =
    data.status === 'ACTIVE'
      ? t.confirmed(formatDate(data.confirmedAt))
      : data.status === 'PENDING'
        ? t.pending
        : t.rejected(data.rejectReason ?? '');

  return (
    <Page testId="company-page">
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <PageTitle testId="company-title">{data.name}</PageTitle>
        <CompanyStatusBadge status={data.status} />
      </Box>
      <Muted>{subtitle}</Muted>
      <Columns>
        <Panel gap={18} sx={{ flex: 1 }}>
          <PanelTitle>{t.requisites}</PanelTitle>
          <KeyValueGrid
            columnGap={40}
            rowGap={18}
            items={[
              { label: t.inn, value: data.inn },
              { label: t.kpp, value: data.kpp || '—' },
              { label: t.ogrn, value: data.ogrn || '—' },
              { label: t.legalAddress, value: data.legalAddress || '—' },
              { label: t.yourRoles, value: myRoles.join(', ') || '—' },
            ]}
          />
        </Panel>
        <Panel gap={14} sx={{ width: 420, flexShrink: 0 }} data-testid="company-members">
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
            <PanelTitle>{t.members}</PanelTitle>
            <Tooltip title={t.addMemberSoon}>
              <span>
                <Button variant="outlined" disabled>
                  {t.addMember}
                </Button>
              </span>
            </Tooltip>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Avatar name={user?.fullName} size={36} />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1">{user?.fullName}</Typography>
              <Typography variant="caption" component="div" sx={{ color: c('text-muted') }}>
                {[user?.login, ...myRoles].join(' · ')}
              </Typography>
            </Box>
          </Box>
          <Typography variant="caption" sx={{ color: c('text-muted') }}>
            {t.membersHint}
          </Typography>
        </Panel>
      </Columns>
    </Page>
  );
}
