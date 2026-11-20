import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { companiesApi, queryKeys } from '../api/endpoints';
import { errorMessage, isApiError } from '../api/errors';
import type { CompanyView } from '../api/types';
import { useNotify } from '../components/Notifier';
import { Cell, DataTable, type Column } from '../components/ui/DataTable';
import { TextField } from '../components/ui/FormField';
import { Notice } from '../components/ui/Notice';
import { Muted, Page, PageTitle } from '../components/ui/Panel';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatDate } from '../lib/format';
import { texts } from '../texts/ru';
import { c } from '../theme/tokens';

const t = texts.adminCompanies;
const LIST_QUERY = { status: 'PENDING' as const, size: 100, sort: 'createdAt,asc' };

// UI-06. Подтверждение компаний (ui-requirements.md §6.6, макет «UI-06 Подтверждение компаний»).
export function AdminCompaniesPage() {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [confirming, setConfirming] = useState<CompanyView | null>(null);
  const [rejecting, setRejecting] = useState<CompanyView | null>(null);

  const query = useQuery({
    queryKey: queryKeys.companiesForConfirmation(LIST_QUERY),
    queryFn: () => companiesApi.listForConfirmation(LIST_QUERY),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'companies'] });

  const columns: Column<CompanyView>[] = [
    { key: 'name', title: t.columns.name, width: 150, render: (r) => <Cell strong>{r.name}</Cell> },
    { key: 'inn', title: t.columns.inn, width: 120, render: (r) => <Cell>{r.inn}</Cell> },
    { key: 'ogrn', title: t.columns.ogrn, width: 140, render: (r) => <Cell>{r.ogrn || '—'}</Cell> },
    { key: 'address', title: t.columns.address, render: (r) => <Cell>{r.legalAddress || '—'}</Cell> },
    { key: 'created', title: t.columns.created, width: 100, render: (r) => <Cell>{formatDate(r.createdAt)}</Cell> },
    {
      key: 'decision',
      title: t.columns.decision,
      width: 264,
      render: (r) => (
        <Box sx={{ display: 'flex', gap: '8px' }}>
          <Button variant="contained" onClick={() => setConfirming(r)} data-testid="action-confirm-company">
            {t.confirm}
          </Button>
          <Button variant="outlined" onClick={() => setRejecting(r)} data-testid="action-reject-company">
            {t.reject}
          </Button>
        </Box>
      ),
    },
  ];

  return (
    <Page testId="admin-companies">
      <PageTitle>{t.title}</PageTitle>
      <Muted>{t.intro}</Muted>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState text={t.empty} />
      ) : (
        <DataTable
          label={t.title}
          columns={columns}
          rows={query.data.content}
          rowKey={(r) => r.id}
          rowTestId={(r) => `admin-companies-row-${r.inn}`}
        />
      )}

      {confirming ? (
        <ConfirmDialog
          company={confirming}
          onClose={() => setConfirming(null)}
          onDone={(name) => {
            setConfirming(null);
            notify.success(t.confirmed(name));
            void refresh();
          }}
          onStale={() => {
            setConfirming(null);
            notify.info(t.alreadyProcessed);
            void refresh();
          }}
        />
      ) : null}
      {rejecting ? (
        <RejectDialog
          company={rejecting}
          onClose={() => setRejecting(null)}
          onDone={(name) => {
            setRejecting(null);
            notify.success(t.rejected(name));
            void refresh();
          }}
          onStale={() => {
            setRejecting(null);
            notify.info(t.alreadyProcessed);
            void refresh();
          }}
        />
      ) : null}
    </Page>
  );
}

const isStale = (error: unknown) =>
  isApiError(error) && (error.code === 'ILLEGAL_TRANSITION' || error.code === 'VERSION_CONFLICT');

function ConfirmDialog({
  company,
  onClose,
  onDone,
  onStale,
}: {
  company: CompanyView;
  onClose: () => void;
  onDone: (name: string) => void;
  onStale: () => void;
}) {
  const mutation = useMutation({
    mutationFn: () => companiesApi.confirm(company.id),
    onSuccess: () => onDone(company.name),
    onError: (error) => {
      if (isStale(error)) onStale();
    },
  });
  const innNotFound = isApiError(mutation.error) && mutation.error.code === 'COMPANY_INN_NOT_FOUND';
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t.confirmTitle(company.name)}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '12px' }}>
          <Typography variant="body1" sx={{ color: c('text-secondary') }}>
            {t.confirmText}
          </Typography>
          {mutation.isError ? (
            <Notice tone="error">
              {innNotFound ? t.innNotFoundOnConfirm : errorMessage(mutation.error, { mutation: true })}
            </Notice>
          ) : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button
          variant="contained"
          loading={mutation.isPending}
          onClick={() => mutation.mutate()}
          data-testid="action-confirm-company-confirm"
        >
          {t.confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function RejectDialog({
  company,
  onClose,
  onDone,
  onStale,
}: {
  company: CompanyView;
  onClose: () => void;
  onDone: (name: string) => void;
  onStale: () => void;
}) {
  const [reason, setReason] = useState('');
  const [comment, setComment] = useState('');
  const [touched, setTouched] = useState(false);
  const mutation = useMutation({
    mutationFn: () => companiesApi.reject(company.id, { reason: reason.trim(), comment: comment.trim() || undefined }),
    onSuccess: () => onDone(company.name),
    onError: (error) => {
      if (isStale(error)) onStale();
    },
  });
  const reasonError = touched && !reason.trim() ? texts.common.required : undefined;
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t.rejectTitle(company.name)}</DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: '16px', pt: '4px' }}>
          <TextField
            label={t.reason}
            required
            value={reason}
            onChange={setReason}
            maxLength={1000}
            placeholder={t.reasonPlaceholder}
            error={reasonError}
            testId="admin-companies-reject-reason"
          />
          <TextField label={t.comment} value={comment} onChange={setComment} maxLength={4000} rows={3} />
          {mutation.isError ? <Notice tone="error">{errorMessage(mutation.error, { mutation: true })}</Notice> : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose}>
          {texts.common.cancel}
        </Button>
        <Button
          variant="contained"
          loading={mutation.isPending}
          onClick={() => {
            setTouched(true);
            if (reason.trim()) mutation.mutate();
          }}
          data-testid="action-reject-company-confirm"
        >
          {t.reject}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
