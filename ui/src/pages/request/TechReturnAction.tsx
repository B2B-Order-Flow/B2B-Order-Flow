import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { stagesApi } from '../../api/endpoints';
import { fieldErrors } from '../../api/errors';
import type { RequestView, StageView } from '../../api/types';
import { TextField } from '../../components/ui/FormField';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c } from '../../theme/tokens';
import { useRequestAction } from './useRequestAction';

const t = texts.rework;

// Возврат с проверки ТВ (T09b): данные заявки нужно исправить, комментарий «что исправить» обязателен.
// Этап TECH_CHECK переходит в RETURNED, заявка — в «На доработке», менеджер получает уведомление.
export function TechReturnAction({ request, stage }: { request: RequestView; stage: StageView }) {
  const [open, setOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string>();
  const send = useRequestAction(
    request.id,
    () => stagesApi.returnToRework(stage.id, { comment: comment.trim(), version: stage.version }),
    t.returned,
    { onSuccess: () => setOpen(false) },
  );
  const serverError = fieldErrors(send.error).comment;

  const submit = () => {
    if (!comment.trim()) {
      setError(texts.common.required);
      return;
    }
    setError(undefined);
    send.mutate(undefined);
  };

  return (
    <>
      <Button variant="outlined" onClick={() => setOpen(true)} data-testid="action-tech-return">
        {t.returnAction}
      </Button>
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t.returnTitle}</DialogTitle>
        <DialogContent>
          <Stack sx={{ gap: '14px', pt: '4px' }}>
            <Typography variant="body1" sx={{ color: c('text-secondary') }}>
              {t.returnText}
            </Typography>
            <TextField
              label={t.whatToFix}
              required
              value={comment}
              onChange={setComment}
              rows={4}
              maxLength={4000}
              autoFocus
              error={error ?? serverError}
              testId="tech-return-comment"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button variant="text" onClick={() => setOpen(false)}>
            {texts.common.cancel}
          </Button>
          <Button
            variant="contained"
            loading={send.isPending}
            onClick={submit}
            data-testid="action-tech-return-confirm"
          >
            {t.returnConfirm}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

/** Возврат доступен исполнителю этапа «Проверка ТВ»; повторная ТВ (RECHECK_TECH) так не возвращается. */
export function TechReturn({ request }: { request: RequestView }) {
  const { user } = useSession();
  const stage = request.stages?.find(
    (s) => s.type === 'TECH_CHECK' && s.status === 'IN_PROGRESS' && s.assigneeId === user?.id,
  );
  return stage ? <TechReturnAction request={request} stage={stage} /> : null;
}
