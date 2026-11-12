import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PaymentFormView {
  paymentId: string;
  merchantName: string;
  amount: number;
  currency: string;
  status: 'CREATED' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
  attemptUsed: boolean;
  expiresAt: string;
  serverTime: string;
  acceptedBrands: string[];
  resultUrl: string | null;
}

export interface PaymentResult {
  status: 'APPROVED' | 'DECLINED';
  responseCode: string;
  message: string;
  redirectUrl: string;
}

export interface CardForm {
  pan: string;
  securityCode: string;
  cardHolderName: string;
  expiry: string;
}

export interface Problem {
  status: number;
  detail: string;
  code?: string;
  fields?: Record<string, string>;
  resultUrl?: string;
}

/** API stranice banke. Podaci kartice idu iskljucivo banci i nigde se ne cuvaju u pregledacu. */
@Injectable({ providedIn: 'root' })
export class PaymentApi {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/payments`;

  view(paymentId: string) {
    return firstValueFrom(this.http.get<PaymentFormView>(`${this.api}/${encodeURIComponent(paymentId)}`));
  }

  pay(paymentId: string, form: CardForm) {
    return firstValueFrom(
      this.http.post<PaymentResult>(`${this.api}/${encodeURIComponent(paymentId)}`, form),
    );
  }

  cancel(paymentId: string) {
    return firstValueFrom(
      this.http.post<PaymentResult>(`${this.api}/${encodeURIComponent(paymentId)}/cancel`, {}),
    );
  }
}

export function problemOf(error: unknown): Problem {
  if (error instanceof HttpErrorResponse) {
    if (error.error && typeof error.error === 'object' && 'detail' in error.error) {
      return error.error as Problem;
    }
    if (error.status === 0 || error.status >= 502) {
      return { status: error.status, detail: 'Banka trenutno nije dostupna. Pokušajte ponovo.' };
    }
  }
  return { status: 0, detail: 'Došlo je do greške. Pokušajte ponovo.' };
}

const moneyFormat = new Intl.NumberFormat('sr-Latn-RS', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function amount(value: number): string {
  return moneyFormat.format(value);
}
