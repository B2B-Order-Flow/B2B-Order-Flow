import type { NotificationView } from '../api/types';
import { texts } from '../texts/ru';
import { formatDuration } from './sla';

const t = texts.notifications;

export interface NotificationReader {
  /** Номер заявки, если карточка доступна. */
  requestNumber?: string;
  client: boolean;
  coordinator: boolean;
}

/**
 * Текст по notifications.md, раздел 4. Причина есть в ответе ядра только у отказа компании. У событий заявки
 * нет статуса, причины и комментария, поэтому подробности пользователь видит в карточке заявки.
 */
export function notificationText(item: NotificationView, reader: NotificationReader): string {
  const stageType = item.payload?.stageType;
  const minutesLeft = item.payload?.minutesLeft;
  const build = t.events[item.eventType];
  const n = reader.requestNumber ? ` ${reader.requestNumber}` : '';
  if (!build) return item.requestId ? t.unknownRequest(n) : t.unknown;
  return build({
    n,
    s: stageType ? ` «${texts.stageType[stageType]}»` : '',
    left: minutesLeft === undefined ? '' : formatDuration(minutesLeft, false),
    kind: item.payload?.deadlineKind,
    reason: typeof item.payload?.reason === 'string' ? item.payload.reason.trim() : '',
    client: reader.client,
    coordinator: reader.coordinator,
  });
}

/** Есть что отмечать: по счётчику или по уже загруженному списку, если запрос счётчика не удался. */
export function canReadAll(unreadCount: number, items: NotificationView[] = []): boolean {
  return unreadCount > 0 || items.some((item) => !item.readAt);
}

/** Куда ведёт клик (notifications.md, разделы 4 и 4.6). У экспорта экрана пока нет. */
export function notificationTarget(item: NotificationView): string | null {
  if (item.requestId) return `/requests/${item.requestId}`;
  // Получатель отказа — владелец: /company/new открыт только пользователю без роли, причина показана на /company
  if (item.eventType === 'COMPANY_CONFIRMED' || item.eventType === 'COMPANY_REJECTED') return '/company';
  return null;
}
