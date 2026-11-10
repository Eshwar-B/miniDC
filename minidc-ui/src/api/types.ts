export type Role = 'ADMIN' | 'MEMBER';

export type LeaseState =
  | 'REQUESTED'
  | 'APPROVED'
  | 'PROVISIONING'
  | 'ACTIVE'
  | 'EXPIRING'
  | 'ENDED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'FAILED';

export type NodeStatus = 'ONLINE' | 'OFFLINE' | 'PAUSED';

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  banned: boolean;
  createdAt: string;
}

export interface TokenResponse {
  token: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
  inviteCode: string;
}

export interface Node {
  id: string;
  ownerId: string;
  name: string;
  status: NodeStatus;
  totalRamMb?: number;
  totalCores?: number;
  totalDiskGb?: number;
  address?: string;
  lastHeartbeat?: string;
}

export interface CreateNodeRequest {
  name: string;
}

export interface CreateNodeResponse {
  nodeId: string;
  agentToken: string;
}

export interface Offer {
  id: string;
  nodeId: string;
  maxRamMb: number;
  maxCores: number;
  maxDiskGb: number;
  maxHours: number;
  windowStart?: string;
  windowEnd?: string;
  autoApprove: boolean;
  allowedUserIds?: string[];
  active: boolean;
}

export interface OfferRequest {
  nodeId: string;
  maxRamMb: number;
  maxCores: number;
  maxDiskGb: number;
  maxHours: number;
  windowStart?: string;
  windowEnd?: string;
  autoApprove: boolean;
  allowedUserIds?: string[];
}

export interface LeaseResponse {
  id: string;
  offerId: string;
  borrowerId: string;
  nodeId: string;
  ramMb: number;
  cores: number;
  diskGb: number;
  requestedHours: number;
  state: LeaseState;
  vmName?: string;
  vmPort?: number;
  vmUser?: string;
  startsAt?: string;
  endsAt?: string;
  createdAt: string;
  updatedAt: string;
  failReason?: string;
}

export interface CreateLeaseRequest {
  offerId: string;
  ramMb: number;
  cores: number;
  diskGb: number;
  requestedHours: number;
}

export interface ConnectResponse {
  url: string;
  token?: string;
  connectionId?: string;
}

export interface InviteResponse {
  code: string;
  expiresAt: string;
}

export interface AuditLog {
  id: number;
  at: string;
  actorId?: string;
  leaseId?: string;
  nodeId?: string;
  event: string;
  detail?: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
}

export interface TelemetryMetric {
  nodeId: string;
  leaseId?: string;
  cpuPercent: number;
  usedRamMb: number;
  totalRamMb: number;
  diskUsedGb: number;
  timestamp: string;
}

export interface EgressPolicy {
  blockLan: boolean;
  privateSubnets: string[];
  blockedPorts: number[];
  wireguardEnabled: boolean;
  exitGatewayEndpoint: string;
}

export interface EgressStatus {
  enabled: boolean;
  gatewayEndpoint: string;
  gatewayPublicKey: string;
  activePeers: number;
  zeroTrustLanIsolation: boolean;
  blockedPorts: number[];
  isolatedSubnets: string[];
}

export interface TrafficVerificationResult {
  allowed: boolean;
  targetIp: string;
  targetPort: number;
  reason: string;
}

