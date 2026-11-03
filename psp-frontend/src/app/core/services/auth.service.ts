import { HttpClient } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LoginResponse,
  MfaSetup,
  Role,
  TokenResponse,
  User,
} from '../models';

interface Session {
  token: string;
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'sep-pay.session';
/** PCI DSS 8.2.8: sesija se prekida posle 15 min neaktivnosti. */
export const IDLE_LIMIT_MS = 15 * 60 * 1000;
const REFRESH_BEFORE_MS = 5 * 60 * 1000;
const CHECK_EVERY_MS = 20 * 1000;
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'scroll', 'mousemove'] as const;

/**
 * Prijava i sesija portala. Token se cuva u sessionStorage (brise se zatvaranjem taba),
 * produzava se samo dok je korisnik aktivan, a posle 15 min neaktivnosti korisnik se odjavljuje.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = `${environment.apiUrl}/auth`;

  private readonly session = signal<Session | null>(this.restore());
  private lastActivity = Date.now();
  private refreshing = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  constructor() {
    const onActivity = () => (this.lastActivity = Date.now());
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
    this.timer = setInterval(() => this.checkSession(), CHECK_EVERY_MS);
    inject(DestroyRef).onDestroy(() => {
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity));
      if (this.timer) clearInterval(this.timer);
    });
  }

  get token(): string | null {
    const s = this.session();
    return s && s.expiresAt > Date.now() ? s.token : null;
  }

  hasRole(role: Role): boolean {
    return this.user()?.role === role;
  }

  homeUrl(): string {
    return this.hasRole('SUPER_ADMIN') ? '/admin/klijenti' : '/klijent/transakcije';
  }

  // ---------- koraci prijave ----------

  login(email: string, password: string) {
    return this.handle(this.http.post<LoginResponse>(`${this.api}/login`, { email, password }));
  }

  firstPassword(preAuthToken: string, newPassword: string, confirmPassword: string) {
    return this.handle(
      this.http.post<LoginResponse>(`${this.api}/password/first`, {
        preAuthToken,
        newPassword,
        confirmPassword,
      }),
    );
  }

  mfaSetup(preAuthToken: string) {
    return firstValueFrom(this.http.post<MfaSetup>(`${this.api}/mfa/setup`, { preAuthToken }));
  }

  mfaActivate(preAuthToken: string, code: string) {
    return this.handle(
      this.http.post<LoginResponse>(`${this.api}/mfa/activate`, { preAuthToken, code }),
    );
  }

  mfaVerify(preAuthToken: string, code: string) {
    return this.handle(
      this.http.post<LoginResponse>(`${this.api}/mfa/verify`, { preAuthToken, code }),
    );
  }

  // ---------- prijavljeni korisnik ----------

  async changePassword(currentPassword: string, newPassword: string, confirmPassword: string) {
    const token = await firstValueFrom(
      this.http.post<TokenResponse>(`${this.api}/password`, {
        currentPassword,
        newPassword,
        confirmPassword,
      }),
    );
    this.updateToken(token);
    await this.reloadUser();
  }

  async reloadUser() {
    const user = await firstValueFrom(this.http.get<User>(`${this.api}/me`));
    const s = this.session();
    if (s) this.store({ ...s, user });
  }

  async logout(reason?: 'idle' | 'expired') {
    if (this.token) {
      try {
        await firstValueFrom(this.http.post(`${this.api}/logout`, {}));
      } catch {
        // odjava je lokalna i kada server nije dostupan
      }
    }
    this.clear();
    await this.router.navigate(['/prijava'], { queryParams: reason ? { razlog: reason } : {} });
  }

  /** Lokalno brisanje sesije (npr. kada server vrati 401). */
  clear() {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // sessionStorage moze biti nedostupan
    }
  }

  // ---------------------------------------------------------------------------

  private async handle(request: Observable<LoginResponse>): Promise<LoginResponse> {
    const response = await firstValueFrom(request);
    if (response.step === 'DONE' && response.accessToken && response.user && response.expiresAt) {
      this.lastActivity = Date.now();
      this.store({
        token: response.accessToken,
        expiresAt: Date.parse(response.expiresAt),
        user: response.user,
      });
    }
    return response;
  }

  private async checkSession() {
    const s = this.session();
    if (!s) return;
    const now = Date.now();
    if (now - this.lastActivity >= IDLE_LIMIT_MS) {
      await this.logout('idle');
      return;
    }
    if (s.expiresAt <= now) {
      this.clear();
      await this.router.navigate(['/prijava'], { queryParams: { razlog: 'expired' } });
      return;
    }
    if (s.expiresAt - now < REFRESH_BEFORE_MS && !this.refreshing) {
      this.refreshing = true;
      try {
        this.updateToken(await firstValueFrom(this.http.post<TokenResponse>(`${this.api}/refresh`, {})));
      } catch {
        // interceptor obradjuje 401; ostale greske se pokusavaju ponovo pri sledecoj proveri
      } finally {
        this.refreshing = false;
      }
    }
  }

  private updateToken(token: TokenResponse) {
    const s = this.session();
    if (s) this.store({ ...s, token: token.accessToken, expiresAt: Date.parse(token.expiresAt) });
  }

  private store(session: Session) {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // bez trajne sesije, radi samo dok je stranica otvorena
    }
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      return session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
