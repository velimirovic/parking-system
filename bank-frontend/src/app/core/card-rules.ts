/**
 * Pravila forme za placanje karticom (specifikacija, korak 4). Ista pravila ponavlja i server banke.
 */

export type CardBrand = 'VISA' | 'MASTERCARD' | 'DINACARD';

export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '');
}

/** Lunova formula (mod 10). */
export function luhn(digits: string): boolean {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let d = digits.charCodeAt(i) - 48;
    if (double) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
    double = !double;
  }
  return sum % 10 === 0;
}

export function validPan(digits: string): boolean {
  return /^\d{13,19}$/.test(digits) && luhn(digits);
}

/** Brend po BIN opsegu: Visa 4, Mastercard 51-55 i 2221-2720, DinaCard 9891. */
export function detectBrand(digits: string): CardBrand | null {
  if (digits.startsWith('9891')) return 'DINACARD';
  if (digits.startsWith('4')) return 'VISA';
  const two = Number(digits.slice(0, 2));
  const four = Number(digits.slice(0, 4));
  if (digits.length >= 2 && two >= 51 && two <= 55) return 'MASTERCARD';
  if (digits.length >= 4 && four >= 2221 && four <= 2720) return 'MASTERCARD';
  return null;
}

/** 4111111111111111 -> "4111 1111 1111 1111" */
export function formatPan(digits: string): string {
  return digits.replace(/(.{4})/g, '$1 ').trim();
}

/** Unos datuma isteka: automatski dodaje "/" posle meseca. */
export function formatExpiry(raw: string): string {
  const d = digitsOnly(raw).slice(0, 4);
  return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
}

export type ExpiryCheck = { ok: true } | { ok: false; message: string };

/** MM/YY, mesec 01-12, kartica vazi do kraja navedenog meseca. */
export function checkExpiry(value: string, today: Date = new Date()): ExpiryCheck {
  const m = /^(\d{2})\/(\d{2})$/.exec(value);
  if (!m) return { ok: false, message: 'Format MM/YY' };
  const month = Number(m[1]);
  const year = 2000 + Number(m[2]);
  if (month < 1 || month > 12) return { ok: false, message: 'Mesec mora biti 01–12' };
  const current = today.getFullYear() * 12 + today.getMonth();
  if (year * 12 + (month - 1) < current) return { ok: false, message: 'Kartica je istekla' };
  return { ok: true };
}

export function validSecurityCode(value: string): boolean {
  return /^\d{3}$/.test(value);
}

export function validHolder(value: string): boolean {
  return /^[\p{L}][\p{L} .'-]{1,99}$/u.test(value.trim());
}
