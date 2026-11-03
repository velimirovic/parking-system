import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MerchantMethods,
  MerchantProfile,
  Page,
  RotatedSecret,
  TransactionDetail,
  TransactionRow,
  TransactionStats,
} from '../models';

/** API administratora klijenta. Server uvek koristi klijenta iz tokena, nikad iz zahteva. */
@Injectable({ providedIn: 'root' })
export class MerchantApiService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/merchant`;

  profile() {
    return firstValueFrom(this.http.get<MerchantProfile>(`${this.api}/profile`));
  }

  updateUrls(urls: Pick<MerchantProfile, 'webhookUrl' | 'successUrl' | 'failedUrl' | 'errorUrl'>) {
    return firstValueFrom(this.http.put<MerchantProfile>(`${this.api}/urls`, urls));
  }

  rotateApiKey() {
    return firstValueFrom(this.http.post<RotatedSecret>(`${this.api}/api-key/rotate`, {}));
  }

  rotateWebhookSecret() {
    return firstValueFrom(this.http.post<RotatedSecret>(`${this.api}/webhook-secret/rotate`, {}));
  }

  methods() {
    return firstValueFrom(this.http.get<MerchantMethods>(`${this.api}/methods`));
  }

  toggleMethod(code: string, active: boolean) {
    return firstValueFrom(this.http.put<MerchantMethods>(`${this.api}/methods/${code}`, { active }));
  }

  updateMethodConfig(code: string, values: Record<string, string>) {
    return firstValueFrom(
      this.http.put<MerchantMethods>(`${this.api}/methods/${code}/config`, { values }),
    );
  }

  transactions(filter: { q: string; status: string; page: number }) {
    let params = new HttpParams().set('q', filter.q).set('page', filter.page).set('size', 15);
    if (filter.status) params = params.set('status', filter.status);
    return firstValueFrom(
      this.http.get<Page<TransactionRow>>(`${this.api}/transactions`, { params }),
    );
  }

  stats() {
    return firstValueFrom(this.http.get<TransactionStats>(`${this.api}/transactions/stats`));
  }

  transaction(id: string) {
    return firstValueFrom(this.http.get<TransactionDetail>(`${this.api}/transactions/${id}`));
  }

  exportCsv(filter: { q: string; status: string }) {
    let params = new HttpParams().set('q', filter.q);
    if (filter.status) params = params.set('status', filter.status);
    return firstValueFrom(
      this.http.get(`${this.api}/transactions/export`, { params, responseType: 'blob' }),
    );
  }
}
