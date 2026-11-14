import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, User } from '../models';

interface StoredSession {
  token: string;
  expiresAt: number;
  user: User;
}

export interface RegisterData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
}

const STORAGE_KEY = 'sep-parking.session';

/**
 * Prijava korisnika parking sistema. Token se cuva u sessionStorage (brise se zatvaranjem taba,
 * a ostaje pri preusmeravanju na PSP i nazad u istom tabu).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = `${environment.apiUrl}/auth`;

  private readonly session = signal<StoredSession | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => {
    const s = this.session();
    return s !== null && s.expiresAt > Date.now();
  });

  get token(): string | null {
    const s = this.session();
    return s && s.expiresAt > Date.now() ? s.token : null;
  }

  async login(email: string, password: string): Promise<User> {
    const res = await firstValueFrom(this.http.post<AuthResponse>(`${this.api}/login`, { email, password }));
    this.store(res);
    return res.user;
  }

  async register(data: RegisterData): Promise<User> {
    const res = await firstValueFrom(this.http.post<AuthResponse>(`${this.api}/register`, data));
    this.store(res);
    return res.user;
  }

  async reloadUser(): Promise<void> {
    const user = await firstValueFrom(this.http.get<User>(`${this.api}/me`));
    this.updateUser(user);
  }

  async setNotifyBefore(minutes: number): Promise<void> {
    const user = await firstValueFrom(
      this.http.put<User>(`${this.api}/me/preferences`, { notifyBeforeMinutes: minutes }),
    );
    this.updateUser(user);
  }

  async logout(reason?: 'expired'): Promise<void> {
    this.clear();
    await this.router.navigate(['/prijava'], { queryParams: reason ? { razlog: reason } : {} });
  }

  clear(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // sessionStorage moze biti nedostupan
    }
  }

  private updateUser(user: User): void {
    const s = this.session();
    if (s) this.persist({ ...s, user });
  }

  private store(res: AuthResponse): void {
    this.persist({ token: res.accessToken, expiresAt: Date.parse(res.expiresAt), user: res.user });
  }

  private persist(session: StoredSession): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // radi i bez trajne sesije dok je stranica otvorena
    }
  }

  private restore(): StoredSession | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as StoredSession;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
