import type { AuditEntry, RequestView, UserView } from '../../api/types';
import { formatSpeed } from '../../lib/format';
import { texts } from '../../texts/ru';

const t = texts.requestCard;
const dayMonth = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit' });
const fullDate = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
const time = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });

/** `13.11.2026 в 11:20` или `13.11 в 11:20`. */
export function dateAt(iso: string | undefined, withYear = true): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return `${(withYear ? fullDate : dayMonth).format(date)} в ${time.format(date)}`;
}

/** «Отправлена 13.11.2026 в 11:20» или «Ещё не отправлена». */
export function submittedLabel(request: RequestView): string {
  return request.submittedAt ? t.submittedAt(dateAt(request.submittedAt)) : t.notSubmitted;
}

/**
 * Менеджер в шапке. Имён сотрудников в ответе API нет (ui-requirements.md §13, вопрос 2),
 * поэтому имя показываем, только когда менеджер — текущий пользователь.
 */
export function managerLabel(request: RequestView, user: UserView | undefined): string {
  if (!request.managerId) return t.noManager;
  if (user && request.managerId === user.id) return t.managerName(user.fullName);
  return t.managerAssignedLabel;
}

export function speedLabel(request: RequestView): string {
  return formatSpeed(request.requestedSpeedMbps);
}

/** Кто выполнил действие истории: «вы», роль или «Система». */
export function actorLabel(entry: AuditEntry, user: UserView | undefined): string {
  if (!entry.actorId) return t.system;
  if (user && entry.actorId === user.id) return user.fullName;
  return entry.actorRole ? texts.roles[entry.actorRole] : t.employee;
}

/**
 * Что произошло. Словаря кодов action в контракте пока нет (ui-requirements.md §13, вопрос 4):
 * показываем переход статуса, а неизвестное действие — «изменение заявки».
 */
export function actionLabel(entry: AuditEntry): string {
  const known = t.actions[entry.action];
  if (known) return known;
  const from =
    entry.fromStatus && entry.fromStatus in texts.requestStatus
      ? texts.requestStatus[entry.fromStatus as keyof typeof texts.requestStatus].label
      : null;
  const to =
    entry.toStatus && entry.toStatus in texts.requestStatus
      ? texts.requestStatus[entry.toStatus as keyof typeof texts.requestStatus].label
      : null;
  if (to) return from ? t.transition(from, to) : t.statusSet(to);
  return t.changed;
}
