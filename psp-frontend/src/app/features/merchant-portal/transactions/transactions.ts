import { Component, computed, inject, signal } from '@angular/core';
import {
  Page,
  TransactionDetail,
  TransactionRow,
  TransactionStats,
  TransactionStatus,
  WebhookSummary,
} from '../../../core/models';
import { MerchantApiService } from '../../../core/services/merchant-api.service';
import {
  errorMessage,
  int,
  money,
  preciseTime,
  STATUS_LABELS,
  statusClass,
  time,
  todayLabel,
  dateTime,
  isToday,
} from '../../../shared/format';

/** S06 - pregled transakcija klijenta: promet, pretraga, status isporuke webhook-a, detalji. */
@Component({
  selector: 'app-transactions',
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss',
})
export class Transactions {
  private readonly api = inject(MerchantApiService);

  protected readonly statuses = Object.keys(STATUS_LABELS) as TransactionStatus[];
  protected readonly labels = STATUS_LABELS;
  protected readonly statusClass = statusClass;
  protected readonly money = money;
  protected readonly preciseTime = preciseTime;
  protected readonly dateTime = dateTime;
  protected readonly today = todayLabel();

  protected readonly query = signal('');
  protected readonly status = signal<TransactionStatus | ''>('');
  protected readonly page = signal<Page<TransactionRow> | null>(null);
  protected readonly stats = signal<TransactionStats | null>(null);
  protected readonly selected = signal<TransactionDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly exporting = signal(false);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly turnover = computed(() => {
    const t = this.stats()?.turnover ?? [];
    return t.length === 0 ? money(0, 'RSD') : t.map((a) => money(a.amount, a.currency)).join(' · ');
  });
  protected readonly count = computed(() => int(this.stats()?.count ?? 0));
  protected readonly successRate = computed(() => {
    const rate = this.stats()?.successRate;
    return rate === null || rate === undefined ? '—' : `${String(rate).replace('.', ',')} %`;
  });

  constructor() {
    this.load(0, true);
  }

  protected rowTime(iso: string) {
    return isToday(iso) ? time(iso) : dateTime(iso);
  }

  protected onSearch(event: Event) {
    this.query.set((event.target as HTMLInputElement).value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(0), 300);
  }

  protected onStatus(event: Event) {
    this.status.set((event.target as HTMLSelectElement).value as TransactionStatus | '');
    this.load(0);
  }

  protected async open(row: TransactionRow) {
    try {
      this.selected.set(await this.api.transaction(row.id));
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  protected webhookText(w: WebhookSummary | null): string {
    if (!w) return '—';
    if (w.status === 'DELIVERED') return `${w.lastHttpStatus ?? 200} · ${w.attempts}×`;
    return `${w.lastHttpStatus ?? '—'} · ${w.attempts}/${w.maxAttempts}`;
  }

  protected webhookColor(w: WebhookSummary | null): string {
    if (!w) return 'var(--text-3)';
    return w.status === 'DELIVERED' ? 'var(--ok)' : w.status === 'PENDING' ? 'var(--warn)' : 'var(--err)';
  }

  protected async exportCsv() {
    this.exporting.set(true);
    try {
      const blob = await this.api.exportCsv({ q: this.query(), status: this.status() });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `transakcije-${new Date().toISOString().slice(0, 10)}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.exporting.set(false);
    }
  }

  protected async load(page: number, withStats = false) {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [result, stats] = await Promise.all([
        this.api.transactions({ q: this.query(), status: this.status(), page }),
        withStats || !this.stats() ? this.api.stats() : Promise.resolve(this.stats()),
      ]);
      this.page.set(result);
      this.stats.set(stats);
      const first = result.items[0];
      if (!this.selected() && first) await this.open(first);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
