// Tipovi odgovora psp-service API-ja (portal za klijente i administratore)

export type Role = 'SUPER_ADMIN' | 'MERCHANT_ADMIN';
export type LoginStage = 'PASSWORD_CHANGE' | 'MFA_SETUP' | 'MFA_VERIFY' | 'DONE';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  merchantId: string | null;
  merchantName: string | null;
  merchantCode: string | null;
  mfaEnabled: boolean;
  lastLoginAt: string | null;
  passwordChangedAt: string | null;
}

export interface LoginResponse {
  step: LoginStage;
  email: string;
  preAuthToken: string | null;
  accessToken: string | null;
  expiresAt: string | null;
  user: User | null;
}

export interface TokenResponse {
  accessToken: string;
  expiresAt: string;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
  qrCode: string;
  issuer: string;
}

/** RFC 7807 ProblemDetail sa dodatnim poljima iz GlobalExceptionHandler-a. */
export interface ApiProblem {
  status: number;
  detail: string;
  code?: string;
  remainingAttempts?: number;
  lockMinutes?: number;
  lockedUntil?: string;
  violations?: string[];
  fields?: Record<string, string>;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

// ---------- super admin ----------

export type MerchantStatus = 'ACTIVE' | 'INACTIVE';

export interface MerchantSummary {
  id: string;
  name: string;
  merchantCode: string;
  adminEmail: string | null;
  activeMethods: string[];
  rateLimitPerSec: number;
  status: MerchantStatus;
  pendingFirstLogin: boolean;
  createdAt: string;
}

export interface MerchantCredentials {
  merchant: MerchantSummary;
  adminEmail: string;
  temporaryPassword: string;
  apiKey: string | null;
  webhookSecret: string | null;
}

export interface CreateMerchantRequest {
  name: string;
  adminEmail: string;
  adminFullName: string;
  webhookUrl: string;
  successUrl: string;
  failedUrl: string;
  errorUrl: string;
  rateLimitPerSec: number;
  methods: string[];
}

export type HealthStatus = 'UP' | 'SLOW' | 'DOWN';

export interface Plugin {
  id: string;
  code: string;
  displayName: string;
  pluginBaseUrl: string;
  supportsRefund: boolean;
  active: boolean;
  subscribedMerchants: number;
  health: { status: HealthStatus; latencyMs: number | null } | null;
  createdAt: string;
}

export interface SystemStatus {
  instanceId: string;
  startedAt: string;
  database: 'UP' | 'DOWN';
  pendingWebhooks: number;
  activeMerchants: number;
  totalMerchants: number;
}

export interface AuditEntry {
  id: string;
  seq: number;
  occurredAt: string;
  actorType: string;
  actorId: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  sourceIp: string | null;
  outcome: 'SUCCESS' | 'FAILURE';
  details: string | null;
  hash: string;
}

export interface ChainVerification {
  valid: boolean;
  checkedEntries: number;
  brokenAtSeq: number | null;
}

// ---------- administrator klijenta ----------

export interface MerchantProfile {
  name: string;
  merchantCode: string;
  status: MerchantStatus;
  rateLimitPerSec: number;
  apiKeyMasked: string;
  webhookSecretMasked: string;
  webhookUrl: string | null;
  successUrl: string | null;
  failedUrl: string | null;
  errorUrl: string | null;
}

export interface RotatedSecret {
  secret: string;
  masked: string;
}

export interface MerchantMethod {
  code: string;
  displayName: string;
  subscribed: boolean;
  active: boolean;
  availableOnPsp: boolean;
  supportsRefund: boolean;
  lastActive: boolean;
  config: { key: string; maskedValue: string }[];
}

export interface MerchantMethods {
  methods: MerchantMethod[];
  activeCount: number;
}

export type TransactionStatus =
  | 'INIT'
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'ERROR'
  | 'EXPIRED'
  | 'REFUNDED';

export interface WebhookSummary {
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  lastHttpStatus: number | null;
  attempts: number;
  maxAttempts: number;
}

export interface TransactionRow {
  id: string;
  createdAt: string;
  merchantOrderId: string;
  methodCode: string | null;
  methodName: string | null;
  amount: number;
  currency: string;
  status: TransactionStatus;
  webhook: WebhookSummary | null;
}

export interface TransactionStats {
  since: string;
  turnover: { currency: string; amount: number }[];
  count: number;
  successRate: number | null;
  pendingWebhooks: number;
}

export interface Delivery {
  eventType: string;
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  lastHttpStatus: number | null;
  durationMs: number | null;
  attempts: number;
  maxAttempts: number;
  nextAttemptAt: string | null;
  createdAt: string;
}

export interface TransactionDetail {
  id: string;
  merchantOrderId: string;
  merchantTimestamp: string;
  methodCode: string | null;
  methodName: string | null;
  amount: number;
  currency: string;
  status: TransactionStatus;
  stan: string | null;
  pspTimestamp: string | null;
  globalTransactionId: string | null;
  acquirerTimestamp: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  deliveries: Delivery[];
}
