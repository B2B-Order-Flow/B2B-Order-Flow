import { api, newIdempotencyKey } from './client';
import type {
  AuditEntry,
  CommercialTermsPayload,
  OperatorUserView,
  CompanyDecisionRequest,
  CompanyStatus,
  CompanyView,
  LoginRequest,
  LoginResponse,
  Page,
  PageQuery,
  RegisterCompanyRequest,
  RegisterUserRequest,
  RequestListQuery,
  RequestPayload,
  RequestView,
  SlaView,
  Role,
  StageQueueQuery,
  StageView,
  TechResultPayload,
  UserView,
} from './types';

// Операции недели 1 (api-contract.md §4.1, 6.1, 6.2). Пути — внешние, без префикса /api/v1.

export const authApi = {
  login: (body: LoginRequest) => api.post<LoginResponse>('/auth/login', { body, skipAuthHandling: true }),
  register: (body: RegisterUserRequest) => api.post<UserView>('/auth/register', { body, skipAuthHandling: true }),
  me: () => api.get<UserView>('/me'),
};

export const companiesApi = {
  register: (body: RegisterCompanyRequest, idempotencyKey = newIdempotencyKey()) =>
    api.post<CompanyView>('/companies', { body, idempotencyKey }),
  get: (companyId: string) => api.get<CompanyView>(`/companies/${companyId}`),
  listForConfirmation: (query: PageQuery & { status?: CompanyStatus } = { status: 'PENDING' }) =>
    api.get<Page<CompanyView>>('/admin/companies', { query: { ...query } }),
  confirm: (companyId: string, body: CompanyDecisionRequest = {}) =>
    api.post<CompanyView>(`/admin/companies/${companyId}/confirm`, { body }),
  reject: (companyId: string, body: CompanyDecisionRequest) =>
    api.post<CompanyView>(`/admin/companies/${companyId}/reject`, { body }),
};

export const requestsApi = {
  list: (query: RequestListQuery = {}) => api.get<Page<RequestView>>('/requests', { query: { ...query } }),
  get: (requestId: string) => api.get<RequestView>(`/requests/${requestId}`),
  history: (requestId: string, query: PageQuery = {}) =>
    api.get<Page<AuditEntry>>(`/requests/${requestId}/history`, { query: { ...query } }),
  create: (body: RequestPayload, idempotencyKey: string) =>
    api.post<RequestView>('/requests', { body, idempotencyKey }),
  updateDraft: (requestId: string, body: RequestPayload & { version: number }) =>
    api.patch<RequestView>(`/requests/${requestId}`, { body }),
  deleteDraft: (requestId: string, version: number) =>
    api.delete<void>(`/requests/${requestId}`, { query: { version } }),
  submit: (requestId: string, version: number) =>
    api.post<RequestView>(`/requests/${requestId}/submit`, { body: { version } }),
  recheckData: (requestId: string, version: number) =>
    api.post<RequestView>(`/requests/${requestId}/recheck-data`, { body: { version } }),
  claim: (requestId: string, version: number) =>
    api.post<RequestView>(`/requests/${requestId}/claim`, { body: { version } }),
  assignManager: (requestId: string, managerId: string, version: number) =>
    api.post<RequestView>(`/requests/${requestId}/assign-manager`, { body: { managerId, version } }),
  // Неделя 2 (api-contract.md §6.2, 6.5, 6.13)
  commercialTerms: (requestId: string, body: CommercialTermsPayload) =>
    api.post<RequestView>(`/requests/${requestId}/commercial-terms`, { body }),
  acceptOffer: (requestId: string, version: number) =>
    api.post<RequestView>(`/requests/${requestId}/contract/accept-offer`, { body: { version } }),
  /** Таймеры этапов и контрольные сроки (оператору; клиенту SLA не отдаётся). */
  sla: (requestId: string) => api.get<SlaView>(`/requests/${requestId}/sla`),
};

// Этапы — неделя 2 (api-contract.md §6.4, 6.13).
export const stagesApi = {
  queue: (query: StageQueueQuery) => api.get<Page<StageView>>('/stages', { query: { ...query } }),
  my: () => api.get<Page<StageView>>('/stages/my'),
  claim: (stageId: string, version: number) => api.post<StageView>(`/stages/${stageId}/claim`, { body: { version } }),
  assign: (stageId: string, assigneeId: string, version: number) =>
    api.post<StageView>(`/stages/${stageId}/assign`, { body: { assigneeId, version } }),
  techResult: (stageId: string, body: TechResultPayload) =>
    api.post<StageView>(`/stages/${stageId}/tech-result`, { body }),
};

// Сотрудники оператора с ролью — для назначения менеджера и исполнителя (api-contract.md §6.1).
export const usersApi = {
  byRole: (role: Role) => api.get<Page<OperatorUserView>>('/users', { query: { role, size: 100 } }),
};

// Ключи кэша TanStack Query: одно место, чтобы инвалидировать согласованно.
export const queryKeys = {
  me: ['me'] as const,
  company: (companyId: string) => ['company', companyId] as const,
  companiesForConfirmation: (query: object) => ['admin', 'companies', query] as const,
  requests: (query: object) => ['requests', query] as const,
  requestsAll: ['requests'] as const,
  request: (requestId: string) => ['request', requestId] as const,
  history: (requestId: string, query: object) => ['request', requestId, 'history', query] as const,
  stagesQueue: (query: object) => ['stages', 'queue', query] as const,
  stagesMy: ['stages', 'my'] as const,
  stagesAll: ['stages'] as const,
  sla: (requestId: string) => ['request', requestId, 'sla'] as const,
  usersByRole: (role: string) => ['users', role] as const,
};
