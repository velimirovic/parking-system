import { Component, inject, signal } from '@angular/core';
import { Plugin, SystemStatus } from '../../../core/models';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { dateTime, errorMessage } from '../../../shared/format';
import { AdminCounts } from '../admin-counts';

/**
 * S07 - registar plugin-ova (metoda placanja) i stanje sistema. Nova metoda se registruje
 * u toku rada, bez gasenja PSP Core-a.
 */
@Component({
  selector: 'app-plugins',
  templateUrl: './plugins.html',
  styleUrl: './plugins.scss',
})
export class Plugins {
  private readonly api = inject(AdminApiService);
  private readonly counts = inject(AdminCounts);

  protected readonly plugins = signal<Plugin[]>([]);
  protected readonly system = signal<SystemStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly busy = signal<string | null>(null);
  protected readonly form = signal({ code: '', displayName: '', pluginBaseUrl: 'http://', supportsRefund: true });
  protected readonly dateTime = dateTime;

  constructor() {
    this.load();
  }

  protected host(url: string) {
    return url.replace(/^https?:\/\//, '');
  }

  protected healthLabel(p: Plugin) {
    switch (p.health?.status) {
      case 'UP':
        return 'Up';
      case 'SLOW':
        return 'Spor';
      default:
        return 'Down';
    }
  }

  protected healthClass(p: Plugin) {
    if (!p.active) return 'gr';
    return p.health?.status === 'UP' ? 'ok' : p.health?.status === 'SLOW' ? 'am' : 'rd';
  }

  protected setField(field: 'code' | 'displayName' | 'pluginBaseUrl', event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.form.update((f) => ({ ...f, [field]: value }));
  }

  protected setRefund(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.form.update((f) => ({ ...f, supportsRefund: checked }));
  }

  protected async register(event: Event) {
    event.preventDefault();
    this.busy.set('new');
    this.error.set(null);
    try {
      const created = await this.api.registerPlugin({ ...this.form(), code: this.form().code.trim().toUpperCase() });
      this.plugins.update((list) => [...list, created]);
      this.counts.plugins.set(this.plugins().length);
      this.showForm.set(false);
      this.form.set({ code: '', displayName: '', pluginBaseUrl: 'http://', supportsRefund: true });
      this.flash(`Metoda ${created.code} je registrovana.`);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(null);
    }
  }

  protected async toggle(p: Plugin) {
    if (p.active && !confirm(`Isključiti ${p.code} za sve klijente?`)) return;
    this.busy.set(p.id);
    this.error.set(null);
    try {
      const updated = await this.api.updatePlugin(p.id, { active: !p.active });
      this.plugins.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(null);
    }
  }

  protected async load() {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [plugins, system] = await Promise.all([this.api.plugins(), this.api.system()]);
      this.plugins.set(plugins);
      this.system.set(system);
      this.counts.plugins.set(plugins.length);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  private flash(message: string) {
    this.notice.set(message);
    setTimeout(() => this.notice.set(null), 5000);
  }
}
