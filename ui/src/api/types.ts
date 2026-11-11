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
  status?: RequestStatus;
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
