import {
  AuditLog,
  ConnectResponse,
  CreateLeaseRequest,
  CreateNodeRequest,
  CreateNodeResponse,
  InviteResponse,
  LeaseResponse,
  LoginRequest,
  Node,
  Offer,
  OfferRequest,
  RegisterRequest,
  TokenResponse,
  User,
} from './types';

let tokenGetter: () => string | null = () => localStorage.getItem('minidc_token');

export function setTokenProvider(getter: () => string | null) {
  tokenGetter = getter;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenGetter();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMsg = `Request failed (${response.status})`;
    try {
      const errorJson = await response.json();
      if (errorJson.message) {
        errorMsg = errorJson.message;
      }
    } catch {
      // Use default status text
    }
    throw new Error(errorMsg);
  }

  if (response.status === 204) {
    return {} as T;
  }

  return response.json();
}

export const api = {
  // Auth
  register: (data: RegisterRequest) =>
    request<TokenResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),

  login: (data: LoginRequest) =>
    request<TokenResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),

  logout: () =>
    request<void>('/api/auth/logout', { method: 'POST' }),

  getMe: () => request<User>('/api/me'),

  // Nodes
  createNode: (data: CreateNodeRequest) =>
    request<CreateNodeResponse>('/api/nodes', { method: 'POST', body: JSON.stringify(data) }),

  getMyNodes: () => request<Node[]>('/api/nodes/mine'),

  pauseNode: (id: string) =>
    request<Node>(`/api/nodes/${id}/pause`, { method: 'PUT' }),

  resumeNode: (id: string) =>
    request<Node>(`/api/nodes/${id}/resume`, { method: 'PUT' }),

  // Offers
  createOffer: (data: OfferRequest) =>
    request<Offer>('/api/offers', { method: 'POST', body: JSON.stringify(data) }),

  updateOffer: (id: string, data: OfferRequest) =>
    request<Offer>(`/api/offers/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  deleteOffer: (id: string) =>
    request<void>(`/api/offers/${id}`, { method: 'DELETE' }),

  getAvailableOffers: () =>
    request<Offer[]>('/api/offers/available'),

  // Leases
  createLease: (data: CreateLeaseRequest) =>
    request<LeaseResponse>('/api/leases', { method: 'POST', body: JSON.stringify(data) }),

  getMyLeases: () =>
    request<LeaseResponse[]>('/api/leases/mine'),

  getIncomingLeases: () =>
    request<LeaseResponse[]>('/api/leases/incoming'),

  getLease: (id: string) =>
    request<LeaseResponse>(`/api/leases/${id}`),

  approveLease: (id: string) =>
    request<LeaseResponse>(`/api/leases/${id}/approve`, { method: 'POST' }),

  rejectLease: (id: string, reason?: string) =>
    request<LeaseResponse>(`/api/leases/${id}/reject${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`, {
      method: 'POST',
    }),

  killLease: (id: string, reason?: string) =>
    request<LeaseResponse>(`/api/leases/${id}/kill${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`, {
      method: 'POST',
    }),

  endLease: (id: string) =>
    request<LeaseResponse>(`/api/leases/${id}/end`, { method: 'POST' }),

  getConnectUrl: (id: string) =>
    request<ConnectResponse>(`/api/leases/${id}/connect`),

  // Admin
  createInvite: () =>
    request<InviteResponse>('/api/admin/invites', { method: 'POST' }),

  banUser: (id: string) =>
    request<User>(`/api/admin/users/${id}/ban`, { method: 'POST' }),

  getAdminNodes: () =>
    request<Node[]>('/api/admin/nodes'),

  getAdminLeases: () =>
    request<LeaseResponse[]>('/api/admin/leases'),

  getAdminAudit: () =>
    request<AuditLog[]>('/api/admin/audit'),
};

