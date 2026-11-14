import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { Logo } from '../../shared/components/logo';
import { errorMessage, problemOf } from '../../shared/format';

type Mode = 'prijava' | 'registracija';

/** P01 - prijava i registracija korisnika parking sistema. */
@Component({
  selector: 'app-login',
  imports: [FormsModule, Logo],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** query parametri (withComponentInputBinding) */
  readonly razlog = input<string>();
  readonly povratak = input<string>();

  protected readonly mode = signal<Mode>('prijava');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  // prijava
  protected email = '';
  protected password = '';

  // registracija
  protected reg = { firstName: '', lastName: '', email: '', phone: '', password: '' };
  protected readonly regPassword = signal('');

  /** Pravila lozinke - ista kao na serveru (PasswordPolicy). */
  protected readonly strength = computed(() => {
    const pw = this.regPassword();
    return [
      pw.length >= 12,
      /\p{Lu}/u.test(pw) && /\p{Ll}/u.test(pw),
      /\d/.test(pw),
      /[^\p{L}\d\s]/u.test(pw),
    ];
  });
  protected readonly passwordOk = computed(() => this.strength().every(Boolean));

  protected setMode(mode: Mode) {
    this.mode.set(mode);
    this.error.set(null);
    this.fieldErrors.set({});
  }

  protected async login() {
    if (this.busy()) return;
    this.error.set(null);
    if (!this.email.trim() || !this.password) {
      this.error.set('Unesite e-poštu i lozinku.');
      return;
    }
    this.busy.set(true);
    try {
      await this.auth.login(this.email, this.password);
      await this.goBack();
    } catch (e) {
      this.error.set(errorMessage(e));
      this.password = '';
    } finally {
      this.busy.set(false);
    }
  }

  protected async register() {
    if (this.busy()) return;
    this.error.set(null);
    this.fieldErrors.set({});
    if (!this.passwordOk()) {
      this.error.set('Lozinka mora imati najmanje 12 karaktera, veliko i malo slovo, broj i simbol.');
      return;
    }
    this.busy.set(true);
    try {
      await this.auth.register({ ...this.reg, password: this.regPassword() });
      await this.router.navigate(['/vozila'], { queryParams: { dobrodosli: 1 } });
    } catch (e) {
      const problem = problemOf(e);
      this.fieldErrors.set(problem.fields ?? {});
      this.error.set(problem.fields ? 'Proverite označena polja.' : problem.detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async goBack() {
    const target = this.povratak();
    // samo relativne putanje unutar aplikacije (zastita od open redirect-a)
    if (target && target.startsWith('/') && !target.startsWith('//') && !target.startsWith('/prijava')) {
      await this.router.navigateByUrl(target);
    } else {
      await this.router.navigate(['/pregled']);
    }
  }
}
