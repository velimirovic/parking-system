import { Component, inject, signal } from '@angular/core';
import { AuditEntry, ChainVerification, Page } from '../../../core/models';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { dateTime, errorMessage, preciseTime, shortHash } from '../../../shared/format';
import { AdminCounts } from '../admin-counts';

const ACTIONS = [
  'LOGIN',
  'LOGIN_FAILED',
  'MFA_FAILED',
  'MFA_ENROLLED',
  'ACCOUNT_LOCKED',
  'LOGOUT',
  'PASSWORD_CHANGED',
  'MERCHANT_CREATE',
  'MERCHANT_STATUS_CHANGE',
  'MERCHANT_ACCESS_RESET',
  'CONFIG_CHANGE',
  'KEY_ROTATION',
  'PLUGIN_REGISTER',
  'PLUGIN_UPDATE',
  'DATA_EXPORT',
  'AUDIT_VERIFY',
  'TX_INIT',
  'TX_STATUS_CHANGE',
  'MERCHANT_API_AUTH_FAILED',
];

/** S07 - audit log (PCI DSS zahtev 10): append-only zapisi ulancani hash-om, sa verifikacijom lanca. */
@Component({
  selector: 'app-audit',
  templateUrl: './audit.html',
  styles: `
    .filters {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .filters .in {
      width: auto;
      min-width: 180px;
    }
    .verify {
      font-size: 13px;
      color: var(--text-2);
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .pager {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 16px;
      border-top: 1px solid var(--line);
    }
    .pager div {
      display: flex;
      gap: 6px;
    }
    .details {
      max-width: 280px;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--text-2);
    }
  `,
})
export class Audit {
  private readonly api = inject(AdminApiService);
  private readonly counts = inject(AdminCounts);

  protected readonly actions = ACTIONS;
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly verification = signal<ChainVerification | null>(null);
  protected readonly verifying = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal({ action: '', outcome: '', actor: '' });
  protected readonly shortHash = shortHash;
  protected readonly preciseTime = preciseTime;
  protected readonly dateTime = dateTime;

  private actorTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.load(0);
    this.verify();
  }

  protected actorLabel(e: AuditEntry) {
    return e.actorType === 'PSP_USER' ? (e.actorId ?? '—') : `${e.actorType} · ${e.actorId ?? ''}`;
  }

  protected resource(e: AuditEntry) {
    return e.resourceId ? `${e.resourceType ?? ''} · ${e.resourceId.length > 18 ? e.resourceId.slice(0, 8) + '…' : e.resourceId}` : (e.resourceType ?? '—');
  }

  protected setFilter(field: 'action' | 'outcome' | 'actor', event: Event) {
    const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.filter.update((f) => ({ ...f, [field]: value }));
    if (field === 'actor') {
      if (this.actorTimer) clearTimeout(this.actorTimer);
      this.actorTimer = setTimeout(() => this.load(0), 300);
    } else {
      this.load(0);
    }
  }

  protected async verify() {
    this.verifying.set(true);
    try {
      this.verification.set(await this.api.verifyAuditChain());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.verifying.set(false);
    }
  }

  protected async load(page: number) {
    this.error.set(null);
    try {
      const result = await this.api.auditLog({ page, ...this.filter() });
      this.page.set(result);
      if (!this.filter().action && !this.filter().outcome && !this.filter().actor) {
        this.counts.audit.set(result.totalItems);
      }
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
