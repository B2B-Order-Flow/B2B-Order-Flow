import { api, newIdempotencyKey } from './client';
import type {
  AuditEntry,
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
};
