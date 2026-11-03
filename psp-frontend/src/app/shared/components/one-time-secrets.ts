import { Component, input, output, signal } from '@angular/core';
import { copyToClipboard } from '../format';

export interface SecretItem {
  label: string;
  value: string;
}

/**
 * Jednokratni prikaz tajni (privremena lozinka, API kljuc, webhook tajna). Posle zatvaranja
 * vrednosti vise nisu dostupne - server cuva samo hash ili sifrat.
 */
@Component({
  selector: 'app-one-time-secrets',
  template: `
    <section class="panel strong" role="alertdialog" [attr.aria-label]="title()">
      <div class="hd dark">
        <span>{{ title() }}</span>
        <span class="meta" style="color: var(--navy-muted)">Prikazuje se samo jednom</span>
      </div>
      <div class="body">
        @if (hint()) {
          <p class="note">{{ hint() }}</p>
        }
        @for (item of items(); track item.label) {
          <div class="row">
            <div class="value">
              <span class="lbl">{{ item.label }}</span>
              <span class="mono">{{ item.value }}</span>
            </div>
            <button type="button" class="sb" (click)="copy(item)">
              {{ copied() === item.label ? 'Kopirano' : 'Kopiraj' }}
            </button>
          </div>
        }
        <button type="button" class="btn" (click)="closed.emit()">Sačuvao sam podatke</button>
      </div>
    </section>
  `,
  styles: `
    .body {
      padding: 16px 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .note {
      margin: 0;
    }
    .row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: center;
      padding: 10px 12px;
      background: var(--surface-alt);
      border: 1px solid var(--line-soft);
    }
    .value {
      display: flex;
      flex-direction: column;
      gap: 3px;
      min-width: 0;
    }
    .value .mono {
      font-size: 14px;
      word-break: break-all;
    }
  `,
})
export class OneTimeSecrets {
  readonly title = input.required<string>();
  readonly hint = input('');
  readonly items = input.required<SecretItem[]>();
  readonly closed = output<void>();

  protected readonly copied = signal<string | null>(null);

  protected async copy(item: SecretItem) {
    if (await copyToClipboard(item.value)) {
      this.copied.set(item.label);
      setTimeout(() => this.copied.set(null), 2000);
    }
  }
}
