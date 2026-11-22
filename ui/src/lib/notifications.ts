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
 * Текст по notifications.md, раздел 4. В ответе ядра нет статуса, причины и комментария события,
 * поэтому в тексте их нет: подробности пользователь видит в карточке заявки.
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
    client: reader.client,
    coordinator: reader.coordinator,
  });
}

/** Куда ведёт клик (notifications.md, разделы 4 и 4.6). У экспорта экрана пока нет. */
export function notificationTarget(item: NotificationView): string | null {
  if (item.requestId) return `/requests/${item.requestId}`;
  if (item.eventType === 'COMPANY_CONFIRMED') return '/company';
  if (item.eventType === 'COMPANY_REJECTED') return '/company/new';
  return null;
}
