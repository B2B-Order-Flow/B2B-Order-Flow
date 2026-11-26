// Типы внешнего API. Повторяют модели core-api.yaml / external-api.yaml (недели 1–2) —
// при изменении контракта правятся здесь, в одном месте.

export const COMPANY_ROLES = ['CLIENT_OWNER', 'CLIENT_ADMIN', 'CLIENT_REQUESTER'] as const;

export const OPERATOR_ROLES = [
  'SALES_MANAGER',
  'TECH_SPECIALIST',
  'LAWYER',
  'FINANCE_CONTROLLER',
  'PROCUREMENT_SPECIALIST',
  'B2B_HEAD',
  'ENGINEER',
  'COORDINATOR',
  'ADMIN',
] as const;

export type CompanyRole = (typeof COMPANY_ROLES)[number];
export type OperatorRole = (typeof OPERATOR_ROLES)[number];
export type Role = CompanyRole | OperatorRole;

export const REQUEST_STATUSES = [
  'DRAFT',
  'DATA_CHECK',
  'WITH_MANAGER',
  'TECH_CHECK',
  'IN_APPROVAL',
  'REWORK',
  'CONTRACT_SIGNING',
  'IN_EXECUTION',
  'ACTIVATED',
  'CANCEL_REQUESTED',
  'CLOSED',
  'REJECTED',
  'CANCELLED',
] as const;

export type RequestStatus = (typeof REQUEST_STATUSES)[number];
export type CompanyStatus = 'PENDING' | 'ACTIVE' | 'REJECTED';
export type ContractType = 'OFFER' | 'STANDARD' | 'CLIENT_FORM';
export type TariffType = 'STANDARD' | 'INDIVIDUAL';
export type OfferStatus = 'PROPOSED' | 'SELECTED' | 'DECLINED';

export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  page: PageInfo;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

export interface Violation {
  field: string;
  code: string;
  message: string;
}

export interface Warning {
  code: string;
  message: string;
}

export interface CompanyBrief {
  id: string;
  name: string;
  status: CompanyStatus;
}

export interface UserView {
  id: string;
  login: string;
  fullName: string;
  email: string;
  companyRoles?: CompanyRole[];
  operatorRoles?: OperatorRole[];
  companies?: CompanyBrief[];
}

export interface LoginRequest {
  login: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: UserView;
}

export interface RegisterUserRequest {
  login: string;
  password: string;
  fullName: string;
  email: string;
}

export interface RegisterCompanyRequest {
  name: string;
  inn: string;
  kpp?: string;
  ogrn?: string;
  legalAddress: string;
}

export interface CompanyView {
  id: string;
  name: string;
  inn: string;
  kpp?: string;
  ogrn?: string;
  legalAddress?: string;
  status: CompanyStatus;
  rejectReason?: string;
  confirmedAt?: string;
  createdAt: string;
}

export interface CompanyDecisionRequest {
  reason?: string;
  comment?: string;
}

export interface RequestPayload {
  address?: string;
  requestedSpeedMbps?: number;
  desiredDate?: string;
  contactName?: string;
  contactPhone?: string;
  contractPreference?: ContractType;
  clientComment?: string;
}

export interface ServiceOfferView {
  id: string;
  position?: number;
  status: OfferStatus;
  tariffType: TariffType;
  speedMbps?: number;
  discountPercent?: string;
  priceRub?: string;
  termMonths?: number;
  contractType: ContractType;
}

export interface RequestView {
  id: string;
  number: string;
  companyId: string;
  companyName?: string;
  status: RequestStatus;
  previousStatus?: RequestStatus;
  statusChangedAt?: string;
  integrationError?: boolean;
  commitmentLevel?: number;
  routeVersion?: number;
  version: number;
  createdBy?: string;
  managerId?: string;
  createdAt: string;
  updatedAt?: string;
  submittedAt?: string;
  address?: string;
  requestedSpeedMbps?: number;
  desiredDate?: string;
  contactName?: string;
  contactPhone?: string;
  contractPreference?: ContractType;
  clientComment?: string;
  segment?: string;
  isGovCustomer?: boolean;
  debtAmount?: string;
  crmManagerId?: string;
  selectedOfferId?: string;
  offers?: ServiceOfferView[];
  // Неделя 2: результат ТВ, договор, исполнение, акт (core-api.yaml, RequestView)
  requestClass?: 'STANDARD' | 'EXTENDED' | 'LARGE';
  techResult?: TechResult;
  techRejectReasonCode?: string;
  techRejectReason?: string;
  capex?: string;
  constructionDays?: number;
  contractNumber?: string;
  contractSentAt?: string;
  contractConcludedAt?: string;
  workStartedAt?: string;
  activatedAt?: string;
  actSentAt?: string;
  closedAt?: string;
  acceptedByDefault?: boolean;
  /** Маршрут текущей версии в порядке этапов; клиенту — без исполнителей. */
  stages?: StageView[];
  warnings?: Warning[];
}

export interface RequestListQuery extends PageQuery {
  /** Один статус или несколько (ui-requirements.md §6.5: фильтр по нескольким статусам). */
  status?: RequestStatus | RequestStatus[];
  companyId?: string;
  managerId?: string;
  unassigned?: boolean;
}

export interface AuditEntry {
  id: string;
  createdAt: string;
  correlationId?: string;
  actorId?: string;
  actorRole?: Role;
  companyId?: string;
  requestId?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  fromStatus?: string;
  toStatus?: string;
  comment?: string;
  details?: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Неделя 2: этапы, SLA, коммерческие условия, результат ТВ, сотрудники оператора.
// Сверено с core-api.yaml и external-api.yaml недели 2 (PR #41, #42): StageView, SlaTimerView,
// SlaView, SlaPauseView, OperatorUserView.

export const STAGE_TYPES = [
  'TECH_CHECK',
  'RECHECK_TECH',
  'LEGAL_APPROVAL',
  'FINANCE_APPROVAL',
  'PROCUREMENT_APPROVAL',
  'MGMT_APPROVAL',
  'CONTRACT_SIGNING',
  'CONSTRUCTION',
  'INSTALLATION',
  'ACTIVATION',
  'REMEDIATION',
  'CLOSING',
] as const;

export type StageType = (typeof STAGE_TYPES)[number];
export type StageStatus = 'QUEUED' | 'IN_PROGRESS' | 'DONE' | 'RETURNED' | 'REJECTED' | 'SUPERSEDED' | 'CANCELLED';
export type TechResult = 'AVAILABLE' | 'WITH_CONSTRUCTION' | 'NONE';
export type SlaTimerStatus = 'RUNNING' | 'PAUSED' | 'STOPPED';
/** Вид таймера: срок этапа или контрольный срок заявки. */
export type SlaTimerKind =
  'STAGE_SLA' | 'DATA_CHECK' | 'MANAGER_ASSIGNMENT' | 'TECH_RESULT' | 'ACCEPTANCE' | 'CANCEL_RESPONSE';
/** Закрытый справочник причин паузы (sla.md, 6.1). */
export type PauseReason =
  | 'REWORK'
  | 'AWAIT_CLIENT_SIGNATURE'
  | 'AWAIT_CLIENT_DATA'
  | 'NO_ACCESS'
  | 'CLIENT_RESCHEDULED'
  | 'CANCEL_REVIEW'
  | 'RECHECK_TECH';

export interface SlaPauseView {
  id: string;
  reasonCode: PauseReason;
  comment?: string;
  startedAt: string;
  endedAt?: string;
  /** Предельный срок паузы. */
  limitAt?: string;
  extendedCount?: number;
  setBy?: string;
}

/** Таймер срока. Остаток, календарное время и просрочку считает ядро. */
export interface SlaTimerView {
  id: string;
  kind: SlaTimerKind;
  /** Только для kind = STAGE_SLA. */
  stageId?: string;
  stageType?: StageType;
  status: SlaTimerStatus;
  normMinutes?: number;
  startedAt: string;
  warnAt?: string;
  dueAt?: string;
  escalateAt?: string;
  /** Израсходованное время без пауз. */
  elapsedSlaSeconds?: number;
  remainingSlaSeconds?: number;
  /** Календарное время с создания таймера, без вычета пауз. */
  calendarElapsedSeconds?: number;
  warnedAt?: string;
  breachedAt?: string;
  escalationLevel?: number;
  overdue?: boolean;
  /** Текущая пауза, если таймер на паузе. */
  pause?: SlaPauseView;
}

/** GET /requests/{id}/sla: таймеры этапов, контрольные сроки и журнал пауз. */
export interface SlaView {
  requestId: string;
  timers: SlaTimerView[];
  pauses: SlaPauseView[];
}

/**
 * Этап заявки. Названия компании, адреса и сводки SLA в контракте нет: отдаёт ли их ядро
 * в StageView, решат Дима и Кирилл до 24.11 — до тех пор интерфейс на них не опирается.
 */
export interface StageView {
  id: string;
  requestId: string;
  requestNumber?: string;
  type: StageType;
  status: StageStatus;
  queueRole?: Role;
  /** Не отдаётся клиенту. */
  assigneeId?: string;
  /** ФИО исполнителя для ролей оператора; не отдаётся клиенту. */
  assigneeName?: string;
  needsReassignment?: boolean;
  /** ID правил маршрутизации, из-за которых создан этап (R-01 и т. д.). */
  requiredByRules?: string[];
  routeVersion?: number;
  version: number;
  createdAt: string;
  takenAt?: string;
  finishedAt?: string;
}

export interface StageQueueQuery extends PageQuery {
  queueRole: Role;
  status?: StageStatus;
}

export interface CommercialTermsPayload {
  tariffType: TariffType;
  contractType: ContractType;
  discountPercent?: string;
  speedMbps?: number;
  version: number;
}

/** Правка данных в доработке (T09, T09b, T12): переданные поля заменяются, остальные остаются. */
export interface UpdateRequestDataPayload extends RequestPayload {
  version: number;
  tariffType?: TariffType;
  /** Проценты строкой с двумя знаками; обязательна при INDIVIDUAL. */
  discountPercent?: string;
  /** Скорость в предложении услуги. */
  speedMbps?: number;
  contractType?: ContractType;
}

/** Возврат с проверки ТВ (T09b): что исправить. */
export interface StageReturnPayload {
  comment: string;
  version: number;
}

export type RevisionReason = 'SUBMIT' | 'REWORK' | 'RECALC' | 'RECHECK';

/** Одно изменившееся поле между соседними ревизиями. */
export interface RevisionChange {
  field: string;
  previousValue?: string;
  newValue?: string;
}

export interface RequestRevisionView {
  id: string;
  revisionNo: number;
  reason: RevisionReason;
  createdBy?: string;
  createdAt: string;
  params?: Record<string, unknown>;
  changes: RevisionChange[];
}

export interface ContractConcludedPayload {
  contractNumber: string;
  /** Дата подписания YYYY-MM-DD, не позже сегодняшней. */
  concludedAt: string;
  version: number;
}

export interface TechResultPayload {
  result: TechResult;
  capex?: string;
  constructionDays?: number;
  reasonCode?: string;
  comment?: string;
  version: number;
}

/** Сотрудник оператора для назначения (GET /users?role=…). Неактивного назначить нельзя. */
export interface OperatorUserView {
  id: string;
  login: string;
  fullName: string;
  operatorRoles: Role[];
  active: boolean;
}

/** Коды событий (api-contract.md, раздел 7; core-api.yaml, NotificationEventType). */
export type NotificationEventType =
  | 'REQUEST_SUBMITTED'
  | 'REQUEST_ASSIGNED'
  | 'STAGE_QUEUED'
  | 'STAGE_ASSIGNED'
  | 'STAGE_REASSIGN_REQUIRED'
  | 'STATUS_CHANGED'
  | 'APPROVAL_RETURNED'
  | 'TECH_RETURNED'
  | 'CONTRACT_CHANGE_REQUESTED'
  | 'TECH_RECHECK_REQUIRED'
  | 'APPROVAL_REJECTED'
  | 'TECH_RESULT_NONE'
  | 'CONTRACT_READY'
  | 'SERVICE_ACTIVATED'
  | 'DEFECT_CLAIMED'
  | 'ACT_SENT'
  | 'ACCEPTANCE_REMINDER'
  | 'REQUEST_CLOSED'
  | 'CANCEL_REQUESTED'
  | 'CANCEL_TERMS_READY'
  | 'CANCEL_RESOLVED'
  | 'SLA_WARNING'
  | 'SLA_BREACH'
  | 'SLA_ESCALATION'
  | 'PAUSE_LIMIT_WARNING'
  | 'PAUSE_LIMIT_REACHED'
  | 'CONTROL_DEADLINE_WARNING'
  | 'CONTROL_DEADLINE_BREACH'
  | 'INTEGRATION_ERROR'
  | 'COMPANY_CONFIRMED'
  | 'COMPANY_REJECTED'
  | 'EXPORT_READY';

/** Данные для подписи уведомления (notifications.md, раздел 5). */
export interface NotificationPayload {
  stageId?: string;
  stageType?: StageType;
  minutesLeft?: number;
  deadlineKind?: string;
  companyId?: string;
  exportId?: string;
  /** Причина отказа: приходит у COMPANY_REJECTED через REST. */
  reason?: string;
}

export interface NotificationView {
  id: string;
  eventType: NotificationEventType;
  requestId?: string;
  /** Номер заявки; нет у уведомлений компании без заявки. */
  requestNumber?: string;
  payload?: NotificationPayload;
  createdAt: string;
  readAt?: string;
  publishedAt?: string;
}

export interface NotificationQuery extends PageQuery {
  unread?: boolean;
}
