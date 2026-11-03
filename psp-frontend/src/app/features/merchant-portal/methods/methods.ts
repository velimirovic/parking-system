import { Component, computed, inject, signal } from '@angular/core';
import { MerchantMethod, MerchantMethods, MerchantProfile } from '../../../core/models';
import { MerchantApiService } from '../../../core/services/merchant-api.service';
import { OneTimeSecrets, SecretItem } from '../../../shared/components/one-time-secrets';
import { copyToClipboard, errorMessage, METHOD_ABBR } from '../../../shared/format';

type UrlField = 'webhookUrl' | 'successUrl' | 'failedUrl' | 'errorUrl';

interface ConfigField {
  key: string;
  label: string;
}

/** Parametri koje plugin-ovi ocekuju od klijenta; vrednosti se na serveru cuvaju sifrovano. */
const CONFIG_FIELDS: Record<string, ConfigField[]> = {
  CARD: [{ key: 'bankMerchantId', label: 'MERCHANT_ID kod banke' }],
  QR: [
    { key: 'accountNumber', label: 'Račun primaoca' },
    { key: 'recipientName', label: 'Naziv primaoca' },
  ],
  PAYPAL: [
    { key: 'clientId', label: 'PayPal client_id' },
    { key: 'clientSecret', label: 'PayPal client_secret' },
  ],
  CRYPTO: [
    { key: 'walletAddress', label: 'Adresa wallet-a' },
    { key: 'network', label: 'Testnet mreža' },
  ],
};
const GENERIC_FIELDS: ConfigField[] = [{ key: 'accountId', label: 'ID naloga' }];

/** S05 - metode placanja i integracija klijenta. */
@Component({
  selector: 'app-methods',
  imports: [OneTimeSecrets],
  templateUrl: './methods.html',
  styleUrl: './methods.scss',
})
export class Methods {
  private readonly api = inject(MerchantApiService);

  protected readonly data = signal<MerchantMethods | null>(null);
  protected readonly profile = signal<MerchantProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busyCode = signal<string | null>(null);

  protected readonly editingConfig = signal<string | null>(null);
  protected readonly configDraft = signal<Record<string, string>>({});
  protected readonly editingUrl = signal<UrlField | null>(null);
  protected readonly urlDraft = signal('');
  protected readonly secrets = signal<{ title: string; items: SecretItem[] } | null>(null);
  protected readonly copied = signal(false);

  protected readonly abbr = METHOD_ABBR;
  protected readonly urlRows: { field: UrlField; label: string }[] = [
    { field: 'webhookUrl', label: 'Webhook URL' },
    { field: 'successUrl', label: 'SUCCESS_URL' },
    { field: 'failedUrl', label: 'FAILED_URL' },
    { field: 'errorUrl', label: 'ERROR_URL' },
  ];
  protected readonly totalMethods = computed(() => this.data()?.methods.length ?? 0);

  constructor() {
    this.load();
  }

  protected fieldsFor(code: string): ConfigField[] {
    return CONFIG_FIELDS[code] ?? GENERIC_FIELDS;
  }

  protected configSummary(m: MerchantMethod): string {
    if (!m.subscribed) return 'Nije pretplaćen - uključivanjem se pretplaćujete';
    if (m.config.length === 0) return 'Konfiguracija nije podešena';
    const labels = Object.fromEntries(this.fieldsFor(m.code).map((f) => [f.key, f.label]));
    return m.config.map((c) => `${labels[c.key] ?? c.key}: ${c.maskedValue}`).join(' · ');
  }

  protected async toggle(m: MerchantMethod) {
    if (m.lastActive || this.busyCode()) return;
    this.busyCode.set(m.code);
    this.error.set(null);
    try {
      this.data.set(await this.api.toggleMethod(m.code, !m.active));
      this.flash(`${m.displayName}: ${m.active ? 'isključeno' : 'uključeno'}.`);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busyCode.set(null);
    }
  }

  protected startConfig(m: MerchantMethod) {
    this.editingConfig.set(this.editingConfig() === m.code ? null : m.code);
    this.configDraft.set({});
  }

  protected setDraft(key: string, event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.configDraft.update((d) => ({ ...d, [key]: value }));
  }

  protected async saveConfig(m: MerchantMethod) {
    this.busyCode.set(m.code);
    this.error.set(null);
    try {
      this.data.set(await this.api.updateMethodConfig(m.code, this.configDraft()));
      this.editingConfig.set(null);
      this.flash(`Konfiguracija za ${m.displayName} je sačuvana.`);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busyCode.set(null);
    }
  }

  protected urlValue(field: UrlField): string {
    return this.profile()?.[field] ?? '';
  }

  protected startUrl(field: UrlField) {
    this.editingUrl.set(field);
    this.urlDraft.set(this.urlValue(field));
  }

  protected async saveUrl() {
    const field = this.editingUrl();
    const profile = this.profile();
    if (!field || !profile) return;
    this.error.set(null);
    try {
      this.profile.set(
        await this.api.updateUrls({
          webhookUrl: profile.webhookUrl,
          successUrl: profile.successUrl,
          failedUrl: profile.failedUrl,
          errorUrl: profile.errorUrl,
          [field]: this.urlDraft().trim(),
        }),
      );
      this.editingUrl.set(null);
      this.flash('URL je sačuvan.');
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  protected async rotate(kind: 'api' | 'webhook') {
    const what = kind === 'api' ? 'API ključ' : 'webhook HMAC tajnu';
    if (!confirm(`Rotirati ${what}? Stara vrednost prestaje da važi odmah i sistem klijenta mora da se ažurira.`)) {
      return;
    }
    this.error.set(null);
    try {
      const rotated = kind === 'api' ? await this.api.rotateApiKey() : await this.api.rotateWebhookSecret();
      this.secrets.set({
        title: kind === 'api' ? 'Novi API ključ' : 'Nova webhook HMAC tajna',
        items: [{ label: kind === 'api' ? 'API ključ' : 'Webhook HMAC tajna', value: rotated.secret }],
      });
      this.profile.set(await this.api.profile());
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  protected async copyMerchantId() {
    const id = this.profile()?.merchantCode;
    if (id && (await copyToClipboard(id))) {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    }
  }

  private flash(message: string) {
    this.notice.set(message);
    setTimeout(() => this.notice.set(null), 4000);
  }

  private async load() {
    try {
      const [methods, profile] = await Promise.all([this.api.methods(), this.api.profile()]);
      this.data.set(methods);
      this.profile.set(profile);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
