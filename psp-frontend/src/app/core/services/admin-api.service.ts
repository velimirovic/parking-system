import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuditEntry,
  ChainVerification,
  CreateMerchantRequest,
  MerchantCredentials,
  MerchantStatus,
  MerchantSummary,
  Page,
  Plugin,
  SystemStatus,
} from '../models';

/** API super admina: klijenti, plugin-ovi, stanje sistema i audit log. */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/admin`;

  merchants() {
    return firstValueFrom(this.http.get<MerchantSummary[]>(`${this.api}/merchants`));
  }

  createMerchant(request: CreateMerchantRequest) {
    return firstValueFrom(this.http.post<MerchantCredentials>(`${this.api}/merchants`, request));
  }

  setMerchantStatus(id: string, status: MerchantStatus) {
    return firstValueFrom(
      this.http.patch<MerchantSummary>(`${this.api}/merchants/${id}/status`, { status }),
    );
  }

  setRateLimit(id: string, rateLimitPerSec: number) {
    return firstValueFrom(
      this.http.patch<MerchantSummary>(`${this.api}/merchants/${id}/rate-limit`, {
        rateLimitPerSec,
      }),
    );
  }

  resetAccess(id: string) {
    return firstValueFrom(
      this.http.post<MerchantCredentials>(`${this.api}/merchants/${id}/reset-access`, {}),
    );
  }

  plugins() {
    return firstValueFrom(this.http.get<Plugin[]>(`${this.api}/payment-methods`));
  }

  registerPlugin(request: {
    code: string;
    displayName: string;
    pluginBaseUrl: string;
    supportsRefund: boolean;
  }) {
    return firstValueFrom(this.http.post<Plugin>(`${this.api}/payment-methods`, request));
  }

  updatePlugin(id: string, changes: Partial<Pick<Plugin, 'active' | 'pluginBaseUrl' | 'displayName' | 'supportsRefund'>>) {
    return firstValueFrom(this.http.patch<Plugin>(`${this.api}/payment-methods/${id}`, changes));
  }

  system() {
    return firstValueFrom(this.http.get<SystemStatus>(`${this.api}/system`));
  }

  auditLog(filter: { page: number; action: string; outcome: string; actor: string }) {
    let params = new HttpParams()
      .set('page', filter.page)
      .set('size', 25)
      .set('action', filter.action)
      .set('actor', filter.actor);
    if (filter.outcome) params = params.set('outcome', filter.outcome);
    return firstValueFrom(this.http.get<Page<AuditEntry>>(`${this.api}/audit-log`, { params }));
  }

  verifyAuditChain() {
    return firstValueFrom(this.http.get<ChainVerification>(`${this.api}/audit-log/verify`));
  }
}
