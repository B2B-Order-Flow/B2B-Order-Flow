import type { RequestView, Role, StageStatus, StageType, StageView } from '../api/types';

// Согласования недели 3: какую роль какой этап ждёт (status-model.md §4.1, roles.md §6.3).

export const APPROVAL_STAGE_BY_ROLE: Partial<Record<Role, StageType>> = {
  LAWYER: 'LEGAL_APPROVAL',
  FINANCE_CONTROLLER: 'FINANCE_APPROVAL',
  PROCUREMENT_SPECIALIST: 'PROCUREMENT_APPROVAL',
  B2B_HEAD: 'MGMT_APPROVAL',
};

export const APPROVAL_STAGES: ReadonlySet<StageType> = new Set(Object.values(APPROVAL_STAGE_BY_ROLE));

/** Итог этапа согласования для карточки: DONE — согласовано, RETURNED и REJECTED — решения согласующего. */
export const DECIDED_STATUSES: ReadonlySet<StageStatus> = new Set(['DONE', 'RETURNED', 'REJECTED']);

/**
 * Этап согласования активной роли в заявке, ещё не решённый. Менеджер заявки свою заявку
 * не согласует (EC-18): для него этап не возвращается, карточка покажет пояснение.
 */
export function approvalStageFor(
  request: RequestView,
  role: Role | null,
  userId: string | undefined,
  status: StageStatus,
): StageView | undefined {
  const type = role ? APPROVAL_STAGE_BY_ROLE[role] : undefined;
  if (!type || request.status !== 'IN_APPROVAL' || !userId || request.managerId === userId) return undefined;
  return request.stages?.find(
    (s) => s.type === type && s.status === status && (status === 'QUEUED' ? !s.assigneeId : s.assigneeId === userId),
  );
}

/** Менеджер заявки с ролью согласующего видит свой этап, но решать по нему не может (EC-18). */
export function isSelfApproval(request: RequestView, role: Role | null, userId: string | undefined): boolean {
  const type = role ? APPROVAL_STAGE_BY_ROLE[role] : undefined;
  return Boolean(
    type &&
    userId &&
    request.status === 'IN_APPROVAL' &&
    request.managerId === userId &&
    request.stages?.some((s) => s.type === type && (s.status === 'QUEUED' || s.status === 'IN_PROGRESS')),
  );
}
