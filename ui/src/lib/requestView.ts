import type { ContractType, RequestView } from '../api/types';
import { texts } from '../texts/ru';

// Производные значения заявки для экранов. Правил процесса здесь нет — только то, что показать.

/** Тип договора: выбранный вариант услуги, иначе пожелание клиента. */
export function contractTypeOf(request: RequestView): ContractType | undefined {
  const offer = request.offers?.find((o) => o.id === request.selectedOfferId) ?? request.offers?.[0];
  return offer?.contractType ?? request.contractPreference;
}

/** Подсказка клиенту «Нужно ваше действие» (ui-requirements.md §6.5): оферта к акцепту, акт к подписи. */
export function clientActionHint(request: RequestView): string | null {
  if (request.status === 'CONTRACT_SIGNING' && contractTypeOf(request) === 'OFFER')
    return texts.requests.hintAcceptOffer;
  if (request.status === 'ACTIVATED') return texts.requests.hintSignAct;
  return null;
}

/** Заголовок карточки: адрес подключения, иначе номер. */
export function requestTitle(request: RequestView): string {
  return request.address?.trim() || request.number;
}
