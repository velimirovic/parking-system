/** Modeli API-ja parking-service-a. */

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  initials: string;
  notifyBeforeMinutes: number;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface Tariff {
  label: string;
  dayFrom: number;
  dayTo: number;
  from: string;
  to: string;
  pricePerHour: number;
}

export interface ParkingLocation {
  id: number;
  name: string;
  address: string | null;
  capacity: number | null;
}

export interface Zone {
  id: number;
  code: string;
  name: string;
  area: string;
  description: string | null;
  latitude: number | null;
  longitude: number | null;
  currency: string;
  minDurationMinutes: number;
  maxDurationMinutes: number;
  durationStepMinutes: number;
  extensionAllowed: boolean;
  maxExtensions: number | null;
  maxTotalMinutes: number | null;
  active: boolean;
  baseRatePerHour: number;
  chargingNow: boolean;
  currentRate: number | null;
  tariffs: Tariff[];
  locations: ParkingLocation[];
}

export interface Vehicle {
  id: string;
  plate: string;
  brand: string | null;
  model: string | null;
  label: string;
  isDefault: boolean;
  createdAt: string;
}

export interface VehicleRequest {
  plate: string;
  brand: string | null;
  model: string | null;
  makeDefault?: boolean;
}

export type CheckState = 'OK' | 'FAIL' | 'PENDING';

export interface RuleCheck {
  code: string;
  label: string;
  state: CheckState;
  message: string | null;
}

export interface DurationOption {
  minutes: number;
  available: boolean;
}

export interface Quote {
  zoneId: number;
  zoneName: string;
  vehicleId: string | null;
  plate: string | null;
  locationId: number | null;
  durationMinutes: number;
  startsAt: string;
  endsAt: string;
  ratePerHour: number | null;
  amount: number | null;
  currency: string;
  valid: boolean;
  maxAvailableMinutes: number;
  chargingNow: boolean;
  chargingEndsAt: string | null;
  checks: RuleCheck[];
  durations: DurationOption[];
}

export interface QuoteRequest {
  zoneId: number;
  vehicleId: string | null;
  locationId: number | null;
  durationMinutes: number;
}

export interface CreateSessionRequest {
  zoneId: number;
  vehicleId: string;
  locationId: number | null;
  durationMinutes: number;
  expectedAmount: number;
  requestId: string;
}

export type SessionStatus = 'PENDING_PAYMENT' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
export type PaymentStatus = 'INIT' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'ERROR' | 'EXPIRED' | 'REFUNDED';
export type PaymentPurpose = 'PURCHASE' | 'EXTENSION';
export type RefundStatus = 'REQUIRED' | 'REFUNDED';

export interface CreateSessionResponse {
  sessionId: string;
  merchantOrderId: string;
  sessionStatus: SessionStatus;
  paymentStatus: PaymentStatus;
  amount: number;
  currency: string;
  redirectUrl: string | null;
  paymentExpiresAt: string | null;
}

export interface PaymentSummary {
  id: string;
  merchantOrderId: string;
  purpose: PaymentPurpose;
  status: PaymentStatus;
  amount: number;
  currency: string;
  durationMinutes: number;
  paymentMethod: string | null;
  globalTransactionId: string | null;
  paymentExpiresAt: string | null;
  redirectUrl: string | null;
  createdAt: string;
  confirmedAt: string | null;
  refundStatus: RefundStatus | null;
  refundReason: string | null;
}

export interface StatusLogEntry {
  occurredAt: string;
  fromStatus: SessionStatus | null;
  toStatus: SessionStatus;
  event: string;
  note: string | null;
  actor: 'USER' | 'PSP' | 'SYSTEM';
}

export interface ParkingSession {
  id: string;
  status: SessionStatus;
  vehicleId: string;
  plate: string;
  vehicleLabel: string;
  zoneId: number;
  zoneCode: string;
  zoneName: string;
  locationName: string | null;
  durationMinutes: number;
  startsAt: string;
  endsAt: string;
  amount: number;
  currency: string;
  extensionCount: number;
  extensionAllowed: boolean;
  maxExtensions: number | null;
  createdAt: string;
  activatedAt: string | null;
  closedAt: string | null;
  lastPayment: PaymentSummary | null;
  payments: PaymentSummary[] | null;
  statusLog: StatusLogEntry[] | null;
}

export interface OrderResponse {
  merchantOrderId: string;
  purpose: PaymentPurpose;
  paymentStatus: PaymentStatus;
  amount: number;
  currency: string;
  paymentMethod: string | null;
  createdAt: string;
  confirmedAt: string | null;
  globalTransactionId: string | null;
  refundStatus: RefundStatus | null;
  refundReason: string | null;
  session: ParkingSession;
}

export interface HistorySummary {
  spent: number;
  currency: string;
  sessions: number;
  extensions: number;
  refunded: number;
}

export interface HistoryResponse {
  summary: HistorySummary;
  items: ParkingSession[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface PaymentRow {
  merchantOrderId: string;
  createdAt: string;
  plate: string;
  zoneName: string;
  purpose: PaymentPurpose;
  paymentMethod: string | null;
  amount: number;
  currency: string;
  status: PaymentStatus;
  refundStatus: RefundStatus | null;
}

/** RFC 9457 ProblemDetail sa kodom greske. */
export interface ApiProblem {
  status: number;
  detail: string;
  code?: string;
  fields?: Record<string, string>;
}
