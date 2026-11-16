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
// Неделя 2: этапы, коммерческие условия, результат ТВ (api-contract.md §6.4, 6.13, 10.7–10.8).
// В external-api.yaml этих операций ещё нет: поля взяты из api-contract.md и domain-model.md
// (request_stage, sla_timer). Когда контракт сольют, сверить и поправить здесь.

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
export type SlaTimerStatus = 'RUNNING' | 'PAUSED' | 'STOPPED';
export type TechResult = 'AVAILABLE' | 'WITH_CONSTRUCTION' | 'NONE';

/** Таймер срока этапа (domain-model.md, sla_timer). */
export interface SlaTimerView {
  normMinutes: number;
  status: SlaTimerStatus;
  /** WORK — рабочие дни (8 ч), CALENDAR — календарные (sla_norm.calendar_kind). */
  calendarKind?: 'WORK' | 'CALENDAR';
  startedAt: string;
  warnAt?: string;
  dueAt?: string;
  elapsedSlaSeconds: number;
  pauseCount?: number;
}

/** Этап заявки (domain-model.md, request_stage) с полями заявки для таблиц очереди. */
export interface StageView {
  id: string;
  requestId: string;
  requestNumber?: string;
  companyName?: string;
  address?: string;
  type: StageType;
  status: StageStatus;
  queueRole: Role;
  assigneeId?: string;
  needsReassignment?: boolean;
  version: number;
  createdAt: string;
  takenAt?: string;
  finishedAt?: string;
  sla?: SlaTimerView;
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

export interface TechResultPayload {
  result: TechResult;
  capex?: string;
  constructionDays?: number;
  reasonCode?: string;
  comment?: string;
  version: number;
}

/** Сотрудник оператора для выбора исполнителя (GET /users?role=…, api-contract.md §6.1). */
export interface UserBrief {
  id: string;
  login: string;
  fullName: string;
}
