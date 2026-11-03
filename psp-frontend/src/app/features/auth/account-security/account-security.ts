import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { passwordAcceptable, PasswordRules } from '../../../shared/components/password-rules';
import { dateTime, errorMessage, problemOf } from '../../../shared/format';

/** Bezbednost naloga: stanje MFA, poslednja prijava i promena lozinke (PCI DSS 8.3). */
@Component({
  selector: 'app-account-security',
  imports: [PasswordRules],
  template: `
    <div class="page-head">
      <div class="page-title">
        <span class="lbl">Nalog</span>
        <h1>Bezbednost naloga</h1>
      </div>
    </div>

    <div class="wrap">
      <section class="panel info">
        <div class="hd"><span>Prijavljeni korisnik</span></div>
        <div class="kvs">
          <div class="kv"><span>E-pošta</span><span class="mono">{{ user()?.email }}</span></div>
          <div class="kv"><span>Uloga</span><span>{{ roleLabel() }}</span></div>
          @if (user()?.merchantName) {
            <div class="kv"><span>Klijent</span><span>{{ user()?.merchantName }} · <span class="mono">{{ user()?.merchantCode }}</span></span></div>
          }
          <div class="kv">
            <span>Dvofaktorska potvrda</span>
            @if (user()?.mfaEnabled) {
              <span class="b ok"><i></i>TOTP uključen</span>
            } @else {
              <span class="b rd"><i></i>Isključena</span>
            }
          </div>
          <div class="kv"><span>Poslednja prijava</span><span class="mono">{{ format(user()?.lastLoginAt) }}</span></div>
          <div class="kv"><span>Lozinka promenjena</span><span class="mono">{{ format(user()?.passwordChangedAt) }}</span></div>
        </div>
      </section>

      <form class="panel strong change" (submit)="submit($event)" novalidate>
        <div class="hd dark"><span>Promena lozinke</span></div>
        <div class="body">
          <label class="field">
            <span class="lbl">Trenutna lozinka</span>
            <input class="in" type="password" autocomplete="current-password" [value]="current()"
              (input)="current.set(value($event))" />
          </label>
          <label class="field">
            <span class="lbl">Nova lozinka</span>
            <input class="in" type="password" autocomplete="new-password" [value]="next()"
              (input)="next.set(value($event))" />
          </label>
          <label class="field">
            <span class="lbl">Ponovite lozinku</span>
            <input class="in" type="password" autocomplete="new-password" [value]="confirm()"
              (input)="confirm.set(value($event))" />
          </label>
          <app-password-rules [password]="next()" [confirmation]="confirm()" [reused]="reused()" />
          @if (error(); as e) {
            <div class="alert err" role="alert">{{ e }}</div>
          }
          @if (saved()) {
            <div class="alert ok" role="status">Lozinka je promenjena.</div>
          }
          <button type="submit" class="btn lg" [disabled]="!canSubmit() || busy()">Promeni lozinku</button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .wrap {
      display: flex;
      flex-wrap: wrap;
      gap: var(--gap-block);
      align-items: flex-start;
    }
    .info {
      flex: 999 1 420px;
      min-width: 0;
    }
    .kvs {
      padding: 6px 20px 0;
    }
    .kvs .kv:first-child {
      border-top: 0;
    }
    .kvs {
      padding-bottom: 6px;
    }
    .change {
      flex: 1 1 340px;
      min-width: 0;
    }
    .body {
      padding: 16px 20px;
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
  `,
})
export class AccountSecurity {
  private readonly auth = inject(AuthService);
  protected readonly user = this.auth.user;

  protected readonly current = signal('');
  protected readonly next = signal('');
  protected readonly confirm = signal('');
  protected readonly busy = signal(false);
  protected readonly saved = signal(false);
  protected readonly reused = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(
    () => this.current().length > 0 && passwordAcceptable(this.next(), this.confirm()),
  );
  protected readonly roleLabel = computed(() =>
    this.user()?.role === 'SUPER_ADMIN' ? 'Super admin' : 'Administrator klijenta',
  );

  constructor() {
    this.auth.reloadUser().catch(() => undefined);
  }

  protected format(iso: string | null | undefined) {
    return dateTime(iso);
  }

  protected value(event: Event) {
    return (event.target as HTMLInputElement).value;
  }

  protected async submit(event: Event) {
    event.preventDefault();
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set(null);
    this.saved.set(false);
    this.reused.set(false);
    try {
      await this.auth.changePassword(this.current(), this.next(), this.confirm());
      this.current.set('');
      this.next.set('');
      this.confirm.set('');
      this.saved.set(true);
    } catch (e) {
      const problem = problemOf(e);
      this.reused.set(problem.violations?.some((v) => v.startsWith('Razli')) ?? false);
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
