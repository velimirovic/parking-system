import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem, PaymentPurpose, PaymentStatus, SessionStatus } from '../core/models';

const ZONE = 'Europe/Belgrade';
const LOCALE = 'sr-Latn-RS';

const moneyFormat = new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const timeFormat = new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: ZONE });
const timeSecFormat = new Intl.DateTimeFormat(LOCALE, {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
  timeZone: ZONE,
});
const dayMonthFormat = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', timeZone: ZONE });
const dateFormat = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: ZONE });
const longDateFormat = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: ZONE,
});
const monthFormat = new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric', timeZone: ZONE });

/** 1.240,00 RSD */
export function money(amount: number | null | undefined, currency = 'RSD'): string {
  return amount == null ? '—' : `${moneyFormat.format(amount)} ${currency}`;
}

/** 150 RSD/h */
export function rate(amount: number | null | undefined, currency = 'RSD'): string {
  if (amount == null) return '—';
  const whole = Number.isInteger(Number(amount));
  return `${whole ? Number(amount).toFixed(0) : moneyFormat.format(amount)} ${currency}/h`;
}

export function time(iso: string | null | undefined): string {
  return iso ? timeFormat.format(new Date(iso)) : '—';
}

export function timeSec(iso: string | null | undefined): string {
  return iso ? timeSecFormat.format(new Date(iso)) : '—';
}

/** 01.11. 13:52 */
export function shortDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return `${dayMonthFormat.format(d)} ${timeFormat.format(d)}`;
}

/** 01.11.2026. */
export function date(iso: string | null | undefined): string {
  return iso ? dateFormat.format(new Date(iso)) : '—';
}

/** Nedelja, 1. novembar 2026. */
export function longDate(d = new Date()): string {
  const text = longDateFormat.format(d);
  return text.charAt(0).toUpperCase() + text.slice(1) + (text.endsWith('.') ? '' : '.');
}

/** Novembar 2026 */
export function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const text = monthFormat.format(new Date(Date.UTC(y, m - 1, 15)));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Tekuci mesec u formatu GGGG-MM (po lokalnom vremenu Beograda). */
export function currentMonth(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', timeZone: ZONE }).format(
    new Date(),
  );
  return parts.slice(0, 7);
}

/** Poslednjih n meseci, od tekuceg unazad. */
export function lastMonths(n: number): string[] {
  const [y, m] = currentMonth().split('-').map(Number);
  const months: string[] = [];
  for (let i = 0; i < n; i++) {
    const d = new Date(Date.UTC(y, m - 1 - i, 1));
    months.push(`${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`);
  }
  return months;
}

export function greeting(d = new Date()): string {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', hour12: false, timeZone: ZONE }).format(d));
  if (hour < 12) return 'Dobro jutro';
  if (hour < 18) return 'Dobar dan';
  return 'Dobro veče';
}

/** 30 min, 1 h, 1,5 h */
export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = minutes / 60;
  return Number.isInteger(hours) ? `${hours} h` : `${String(hours.toFixed(2)).replace(/0+$/, '').replace('.', ',')} h`;
}

/** 01:22:40 */
export function countdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(Math.floor(total / 3600))}:${p(Math.floor((total % 3600) / 60))}:${p(total % 60)}`;
}

/** Oznaka i klasa statusa sesije. */
export const SESSION_STATUS: Record<SessionStatus, { label: string; cls: string }> = {
  PENDING_PAYMENT: { label: 'Čeka plaćanje', cls: 'am' },
  ACTIVE: { label: 'Aktivna', cls: 'ok' },
  EXPIRED: { label: 'Istekla', cls: 'gr' },
  CANCELLED: { label: 'Otkazana', cls: 'rd' },
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; cls: string }> = {
  INIT: { label: 'U obradi', cls: 'am' },
  PENDING: { label: 'U obradi', cls: 'am' },
  SUCCESS: { label: 'Uspešno', cls: 'ok' },
  FAILED: { label: 'Neuspešno', cls: 'rd' },
  ERROR: { label: 'Greška', cls: 'rd' },
  EXPIRED: { label: 'Isteklo', cls: 'gr' },
  REFUNDED: { label: 'Refundirano', cls: 'bl' },
};

/** Oznaka transakcije: refundacija ima prednost nad statusom placanja. */
export function paymentBadge(p: { status: PaymentStatus; refundStatus?: 'REQUIRED' | 'REFUNDED' | null }): {
  label: string;
  cls: string;
} {
  if (p.refundStatus === 'REQUIRED') return { label: 'Čeka refundaciju', cls: 'bl' };
  if (p.refundStatus === 'REFUNDED') return { label: 'Refundirano', cls: 'bl' };
  return PAYMENT_STATUS[p.status];
}

export const PURPOSE_LABEL: Record<PaymentPurpose, string> = {
  PURCHASE: 'Kupovina',
  EXTENSION: 'Produženje',
};

export function methodLabel(method: string | null | undefined): string {
  switch (method) {
    case 'CARD':
      return 'Platna kartica';
    case 'QR':
      return 'IPS QR';
    case 'PAYPAL':
      return 'PayPal';
    case 'CRYPTO':
      return 'Kriptovaluta';
    case null:
    case undefined:
    case '':
      return '—';
    default:
      return method;
  }
}

const DAY_NAMES = ['Ponedeljak', 'Utorak', 'Sreda', 'Četvrtak', 'Petak', 'Subota', 'Nedelja'];

export function dayName(isoDay: number): string {
  return DAY_NAMES[isoDay - 1] ?? '';
}

/** ProblemDetail iz odgovora, ili opsta poruka kada server nije dostupan. */
export function problemOf(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    if (error.error && typeof error.error === 'object' && 'detail' in error.error) {
      return error.error as ApiProblem;
    }
    if (error.status === 0 || error.status >= 502) {
      return { status: error.status, detail: 'Servis trenutno nije dostupan. Pokušajte ponovo.' };
    }
    return { status: error.status, detail: 'Došlo je do greške. Pokušajte ponovo.' };
  }
  return { status: 0, detail: 'Došlo je do greške. Pokušajte ponovo.' };
}

/** Poruka greske - prva greska polja ima prednost nad opstom porukom. */
export function errorMessage(error: unknown): string {
  const problem = problemOf(error);
  const field = problem.fields ? Object.values(problem.fields)[0] : undefined;
  return field ?? problem.detail;
}

/** Jedinstven ID zahteva (idempotentnost kreiranja naloga). */
export function newRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
