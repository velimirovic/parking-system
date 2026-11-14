import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ParkingSession, PaymentRow } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ParkingApi } from '../../core/services/parking-api.service';
import {
  countdown,
  errorMessage,
  greeting,
  longDate,
  methodLabel,
  money,
  PAYMENT_STATUS,
  PURPOSE_LABEL,
  shortDateTime,
  time,
} from '../../shared/format';

interface SessionCard {
  session: ParkingSession;
  remainingMs: number;
  pct: number;
  expiringSoon: boolean;
  paymentOpen: boolean;
}

/** P02 - pregled: aktivne sesije sa odbrojavanjem, nalozi koji cekaju placanje, poslednja placanja. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(ParkingApi);
  protected readonly auth = inject(AuthService);

  protected readonly sessions = signal<ParkingSession[]>([]);
  protected readonly payments = signal<PaymentRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly now = signal(Date.now());

  protected readonly today = longDate();
  protected readonly hello = greeting();

  protected readonly cards = computed<SessionCard[]>(() => {
    const now = this.now();
    const notifyMs = (this.auth.user()?.notifyBeforeMinutes ?? 5) * 60_000;
    return this.sessions()
      .map((s) => {
        const end = Date.parse(s.endsAt);
        const start = Date.parse(s.startsAt);
        const remainingMs = Math.max(0, end - now);
        const total = Math.max(1, end - start);
        const expires = s.lastPayment?.paymentExpiresAt ? Date.parse(s.lastPayment.paymentExpiresAt) : null;
        return {
          session: s,
          remainingMs,
          pct: Math.max(2, Math.min(100, Math.round((remainingMs / total) * 100))),
          expiringSoon: s.status === 'ACTIVE' && remainingMs <= notifyMs,
          paymentOpen: !!s.lastPayment?.redirectUrl && (expires === null || expires > now),
        };
      })
      // sesija kojoj je isteklo vreme nestaje sa pregleda (istorija je cuva)
      .filter((c) => c.session.status !== 'ACTIVE' || c.remainingMs > 0);
  });

  /** Najranije isticanje medju sesijama koje uskoro isticu (traka upozorenja). */
  protected readonly expiring = computed(() =>
    this.cards()
      .filter((c) => c.expiringSoon)
      .sort((a, b) => a.remainingMs - b.remainingMs)[0] ?? null,
  );

  protected readonly money = money;
  protected readonly time = time;
  protected readonly countdown = countdown;
  protected readonly shortDateTime = shortDateTime;
  protected readonly methodLabel = methodLabel;
  protected readonly PAYMENT_STATUS = PAYMENT_STATUS;
  protected readonly PURPOSE_LABEL = PURPOSE_LABEL;

  constructor() {
    const tick = setInterval(() => this.now.set(Date.now()), 1000);
    // osvezavanje podataka (potvrda placanja, istek) bez ponovnog ucitavanja stranice
    const refresh = setInterval(() => this.load(false), 30_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(tick);
      clearInterval(refresh);
    });
  }

  ngOnInit() {
    this.load(true);
  }

  protected minutesLeft(ms: number): string {
    return `${Math.max(1, Math.ceil(ms / 60_000))} min`;
  }

  protected extensionsLabel(s: ParkingSession): string {
    if (!s.extensionAllowed) return 'Nije dozvoljeno';
    return `${s.extensionCount} / ${s.maxExtensions ?? '∞'}`;
  }

  protected continuePayment(s: ParkingSession) {
    const url = s.lastPayment?.redirectUrl;
    if (url) window.location.href = url;
  }

  private async load(initial: boolean) {
    try {
      const [sessions, payments] = await Promise.all([this.api.currentSessions(), this.api.recentPayments(5)]);
      this.sessions.set(sessions);
      this.payments.set(payments);
      this.error.set(null);
    } catch (e) {
      if (initial) this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
