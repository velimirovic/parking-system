import { Component, computed, inject, signal } from '@angular/core';
import { MerchantSummary, Plugin } from '../../../core/models';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { OneTimeSecrets, SecretItem } from '../../../shared/components/one-time-secrets';
import { errorMessage, problemOf } from '../../../shared/format';
import { AdminCounts } from '../admin-counts';

interface Draft {
  name: string;
  adminEmail: string;
  adminFullName: string;
  webhookUrl: string;
  rateLimitPerSec: string;
  methods: string[];
}

const EMPTY_DRAFT: Draft = {
  name: '',
  adminEmail: '',
  adminFullName: '',
  webhookUrl: '',
  rateLimitPerSec: '50',
  methods: ['CARD'],
};

/** S07 - klijenti PSP-a: registracija (bez restarta PSP-a), status, limit zahteva, reset pristupa. */
@Component({
  selector: 'app-clients',
  imports: [OneTimeSecrets],
  templateUrl: './clients.html',
  styleUrl: './clients.scss',
})
export class Clients {
  private readonly api = inject(AdminApiService);
  private readonly counts = inject(AdminCounts);

  protected readonly merchants = signal<MerchantSummary[]>([]);
  protected readonly methods = signal<Plugin[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly busy = signal(false);
  protected readonly rowBusy = signal<string | null>(null);
  protected readonly draft = signal<Draft>({ ...EMPTY_DRAFT });
  protected readonly credentials = signal<{ title: string; items: SecretItem[] } | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selected = computed(
    () => this.merchants().find((m) => m.id === this.selectedId()) ?? null,
  );

  constructor() {
    this.load();
  }

  protected set(field: keyof Omit<Draft, 'methods'>, event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.draft.update((d) => ({ ...d, [field]: value }));
  }

  protected toggleMethod(code: string, event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.draft.update((d) => ({
      ...d,
      methods: checked ? [...d.methods, code] : d.methods.filter((m) => m !== code),
    }));
  }

  protected async register(event: Event) {
    event.preventDefault();
    const d = this.draft();
    this.formError.set(null);
    this.fieldErrors.set({});
    if (d.methods.length === 0) {
      this.formError.set('Izaberite bar jednu metodu plaćanja.');
      return;
    }
    this.busy.set(true);
    try {
      const result = await this.api.createMerchant({
        name: d.name.trim(),
        adminEmail: d.adminEmail.trim(),
        adminFullName: d.adminFullName.trim(),
        webhookUrl: d.webhookUrl.trim(),
        successUrl: '',
        failedUrl: '',
        errorUrl: '',
        rateLimitPerSec: Number(d.rateLimitPerSec),
        methods: d.methods,
      });
      this.credentials.set({
        title: `Pristup za ${result.merchant.name}`,
        items: [
          { label: 'MERCHANT_ID', value: result.merchant.merchantCode },
          { label: 'E-pošta administratora', value: result.adminEmail },
          { label: 'Privremena lozinka', value: result.temporaryPassword },
          { label: 'API ključ', value: result.apiKey ?? '' },
          { label: 'Webhook HMAC tajna', value: result.webhookSecret ?? '' },
        ],
      });
      this.draft.set({ ...EMPTY_DRAFT });
      await this.load();
    } catch (e) {
      const problem = problemOf(e);
      this.fieldErrors.set(problem.fields ?? {});
      this.formError.set(problem.fields ? 'Proverite označena polja.' : errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected async toggleStatus(m: MerchantSummary) {
    const next = m.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (next === 'INACTIVE' && !confirm(`Deaktivirati klijenta ${m.name}? Njegovi korisnici neće moći da se prijave.`)) {
      return;
    }
    await this.rowAction(m, () => this.api.setMerchantStatus(m.id, next));
  }

  protected async changeLimit(m: MerchantSummary) {
    const value = prompt(`Ograničenje zahteva po sekundi za ${m.name}:`, String(m.rateLimitPerSec));
    if (value === null) return;
    const limit = Number(value);
    if (!Number.isInteger(limit) || limit < 1 || limit > 10000) {
      this.error.set('Limit mora biti ceo broj između 1 i 10000.');
      return;
    }
    await this.rowAction(m, () => this.api.setRateLimit(m.id, limit));
  }

  protected async resetAccess(m: MerchantSummary) {
    if (!confirm(`Resetovati pristup za ${m.adminEmail}? Stara lozinka i uparen Authenticator prestaju da važe.`)) {
      return;
    }
    this.rowBusy.set(m.id);
    this.error.set(null);
    try {
      const result = await this.api.resetAccess(m.id);
      this.credentials.set({
        title: `Novi pristup za ${m.name}`,
        items: [
          { label: 'E-pošta administratora', value: result.adminEmail },
          { label: 'Privremena lozinka', value: result.temporaryPassword },
        ],
      });
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.rowBusy.set(null);
    }
  }

  private async rowAction(m: MerchantSummary, action: () => Promise<MerchantSummary>) {
    this.rowBusy.set(m.id);
    this.error.set(null);
    try {
      const updated = await action();
      this.merchants.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.rowBusy.set(null);
    }
  }

  private async load() {
    try {
      const [merchants, plugins] = await Promise.all([
        this.api.merchants(),
        this.methods().length ? Promise.resolve(this.methods()) : this.api.plugins(),
      ]);
      this.merchants.set(merchants);
      this.methods.set(plugins.filter((p) => p.active));
      this.counts.merchants.set(merchants.length);
      this.counts.plugins.set(plugins.length);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
