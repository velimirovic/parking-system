import { inject, Injectable, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';

/** Brojevi u bocnoj navigaciji super admina; stranice ih azuriraju posle izmena. */
@Injectable({ providedIn: 'root' })
export class AdminCounts {
  private readonly api = inject(AdminApiService);

  readonly merchants = signal<number | null>(null);
  readonly plugins = signal<number | null>(null);
  readonly audit = signal<number | null>(null);

  async refresh() {
    const [merchants, audit] = await Promise.allSettled([
      this.api.merchants(),
      this.api.auditLog({ page: 0, action: '', outcome: '', actor: '' }),
    ]);
    if (merchants.status === 'fulfilled') this.merchants.set(merchants.value.length);
    if (audit.status === 'fulfilled') this.audit.set(audit.value.totalItems);
  }
}
