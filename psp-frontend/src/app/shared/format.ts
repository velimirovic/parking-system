import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem, TransactionStatus } from '../core/models';

const ZONE = 'Europe/Belgrade';
const LOCALE = 'sr-Latn-RS';

const moneyFormat = new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const intFormat = new Intl.NumberFormat(LOCALE);
const timeFormat = new Intl.DateTimeFormat(LOCALE, {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
  timeZone: ZONE,
});
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
  timeZone: ZONE,
});
const longDateFormat = new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'long', timeZone: ZONE });

/** 1.240,00 RSD */
export function money(amount: number, currency: string): string {
  return `${moneyFormat.format(amount)} ${currency}`;
}

export function int(value: number): string {
  return intFormat.format(value);
}

export function time(iso: string | null | undefined): string {
  return iso ? timeFormat.format(new Date(iso)) : '—';
}

/** Vreme sa milisekundama, kao PSP_TIMESTAMP na dizajnu. */
export function preciseTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  return `${timeFormat.format(date)}.${String(date.getMilliseconds()).padStart(3, '0')}`;
}

export function dateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFormat.format(new Date(iso)) : '—';
}

export function isToday(iso: string): boolean {
  const day = (d: Date) => d.toLocaleDateString(LOCALE, { timeZone: ZONE });
  return day(new Date(iso)) === day(new Date());
}

export function todayLabel(): string {
  return `Danas, ${longDateFormat.format(new Date())}`;
}

export function shortHash(hash: string): string {
  return `${hash.slice(0, 4)}…${hash.slice(-4)}`;
}

/** Klasa statusne oznake (.b.ok / .am / .rd / .bl / .gr) za status transakcije. */
export function statusClass(status: TransactionStatus): string {
  switch (status) {
    case 'SUCCESS':
      return 'ok';
    case 'PENDING':
    case 'INIT':
      return 'am';
    case 'REFUNDED':
      return 'bl';
    default:
      return 'rd';
  }
}

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  INIT: 'Init',
  PENDING: 'Pending',
  SUCCESS: 'Success',
  FAILED: 'Failed',
  ERROR: 'Error',
  EXPIRED: 'Expired',
  REFUNDED: 'Refunded',
};

export const METHOD_ABBR: Record<string, string> = {
  CARD: 'VISA',
  QR: 'IPS',
  PAYPAL: 'PP',
  CRYPTO: 'BTC',
};

/** ProblemDetail iz odgovora, ili opsta poruka kada server nije dostupan. */
export function problemOf(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    // poslovna greska PSP-a (ProblemDetail sa kodom) ima prednost i kada je status 503
    if (error.error && typeof error.error === 'object' && 'detail' in error.error && 'code' in error.error) {
      return error.error as ApiProblem;
    }
    if (error.status === 0 || error.status >= 502) {
      return { status: error.status, detail: 'PSP servis trenutno nije dostupan. Pokušajte ponovo.' };
    }
    if (error.error && typeof error.error === 'object' && 'detail' in error.error) {
      return error.error as ApiProblem;
    }
    return { status: error.status, detail: 'Došlo je do greške. Pokušajte ponovo.' };
  }
  return { status: 0, detail: 'Došlo je do greške. Pokušajte ponovo.' };
}

/** Prva poruka greske - polja validacije imaju prednost nad opstom porukom. */
export function errorMessage(error: unknown): string {
  const problem = problemOf(error);
  const field = problem.fields ? Object.values(problem.fields)[0] : undefined;
  const violation = problem.violations?.join(', ');
  return field ?? (violation ? `${problem.detail}: ${violation}` : problem.detail);
}

export async function copyToClipboard(value: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
}
