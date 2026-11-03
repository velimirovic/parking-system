import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
  viewChild,
  WritableSignal,
} from '@angular/core';
import { Router } from '@angular/router';
import { LoginResponse, LoginStage, MfaSetup } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { CodeInput } from '../../../shared/components/code-input';
import { passwordAcceptable, PasswordRules } from '../../../shared/components/password-rules';
import { problemOf, time } from '../../../shared/format';

type Step = 'LOGIN' | Exclude<LoginStage, 'DONE'>;

/**
 * S04 - prijava sa dvofaktorskom potvrdom (PCI DSS zahtev 8).
 * Tok: lozinka -> (nova lozinka pri prvoj prijavi) -> (uparivanje Authenticator-a) -> TOTP kod.
 */
@Component({
  selector: 'app-login',
  imports: [CodeInput, PasswordRules],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** ?razlog=idle|expired iz query parametra. */
  readonly razlog = input<string>();

  protected readonly step = signal<Step>('LOGIN');
  protected readonly busy = signal(false);
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly passwordReused = signal(false);

  protected readonly loginError = signal<string | null>(null);
  protected readonly codeError = signal<string | null>(null);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly showLostDevice = signal(false);

  protected readonly mfaSetup = signal<MfaSetup | null>(null);
  protected readonly secondsLeft = signal(this.totpSecondsLeft());

  private preAuthToken = '';
  private readonly codeInput = viewChild(CodeInput);

  protected readonly sessionNotice = computed(() => {
    switch (this.razlog()) {
      case 'idle':
        return 'Odjavljeni ste posle 15 minuta neaktivnosti.';
      case 'expired':
        return 'Sesija je istekla. Prijavite se ponovo.';
      default:
        return null;
    }
  });

  protected readonly canSavePassword = computed(() =>
    passwordAcceptable(this.newPassword(), this.confirmPassword()),
  );

  protected readonly countdown = computed(() => `0:${String(this.secondsLeft()).padStart(2, '0')}`);

  constructor() {
    const timer = setInterval(() => this.secondsLeft.set(this.totpSecondsLeft()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected async submitLogin(event: Event) {
    event.preventDefault();
    if (this.busy() || !this.email() || !this.password()) {
      this.loginError.set('Unesite e-poštu i lozinku.');
      return;
    }
    await this.run(this.loginError, () => this.auth.login(this.email().trim(), this.password()));
    this.password.set('');
  }

  protected async submitNewPassword(event: Event) {
    event.preventDefault();
    if (!this.canSavePassword() || this.busy()) return;
    this.passwordReused.set(false);
    await this.run(this.passwordError, () =>
      this.auth.firstPassword(this.preAuthToken, this.newPassword(), this.confirmPassword()),
    );
  }

  protected async submitCode(code: string) {
    if (this.busy() || code.length !== 6) return;
    const verify =
      this.step() === 'MFA_SETUP'
        ? () => this.auth.mfaActivate(this.preAuthToken, code)
        : () => this.auth.mfaVerify(this.preAuthToken, code);
    const ok = await this.run(this.codeError, verify);
    if (!ok) this.codeInput()?.clear();
  }

  protected restart() {
    this.step.set('LOGIN');
    this.preAuthToken = '';
    this.mfaSetup.set(null);
    this.codeError.set(null);
    this.passwordError.set(null);
    this.newPassword.set('');
    this.confirmPassword.set('');
  }

  /** Izvrsava korak prijave i prelazi na sledeci; greske se prikazuju na odgovarajucoj kartici. */
  private async run(errorTarget: WritableSignal<string | null>, action: () => Promise<LoginResponse>) {
    this.busy.set(true);
    errorTarget.set(null);
    try {
      const response = await action();
      await this.advance(response);
      return true;
    } catch (error) {
      const problem = problemOf(error);
      if (problem.code === 'LOGIN_SESSION_EXPIRED') {
        this.restart();
        this.loginError.set(problem.detail);
      } else if (problem.code === 'ACCOUNT_LOCKED') {
        this.restart();
        this.loginError.set(
          `Nalog je zaključan zbog previše neuspelih pokušaja${
            problem.lockedUntil ? ` do ${time(problem.lockedUntil).slice(0, 5)}` : ''
          }.`,
        );
      } else if (problem.remainingAttempts !== undefined) {
        const what = problem.code === 'INVALID_MFA_CODE' ? 'Kod nije ispravan ili je istekao' : 'Pogrešna lozinka';
        errorTarget.set(
          `${what}. Još ${problem.remainingAttempts} ${problem.remainingAttempts === 1 ? 'pokušaj' : 'pokušaja'} pre zaključavanja na ${problem.lockMinutes ?? 30} min.`,
        );
      } else if (problem.code === 'PASSWORD_POLICY') {
        this.passwordReused.set(problem.violations?.some((v) => v.startsWith('Razli')) ?? false);
        errorTarget.set(`${problem.detail}: ${problem.violations?.join(', ')}`);
      } else {
        errorTarget.set(problem.detail);
      }
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async advance(response: LoginResponse) {
    if (response.step === 'DONE') {
      await this.router.navigateByUrl(this.auth.homeUrl());
      return;
    }
    this.preAuthToken = response.preAuthToken ?? '';
    this.email.set(response.email);
    if (response.step !== 'PASSWORD_CHANGE') {
      this.newPassword.set('');
      this.confirmPassword.set('');
    }
    this.step.set(response.step);
    if (response.step === 'MFA_SETUP') {
      this.mfaSetup.set(await this.auth.mfaSetup(this.preAuthToken));
    }
    if (response.step === 'MFA_SETUP' || response.step === 'MFA_VERIFY') {
      setTimeout(() => this.codeInput()?.clear());
    }
  }

  private totpSecondsLeft() {
    return 30 - (Math.floor(Date.now() / 1000) % 30);
  }
}
