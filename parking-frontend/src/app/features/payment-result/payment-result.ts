import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderResponse } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ParkingApi } from '../../core/services/parking-api.service';
import { Logo } from '../../shared/components/logo';
import { errorMessage, methodLabel, money, time } from '../../shared/format';

type View = 'pending' | 'success' | 'failed' | 'error';

/** Koliko dugo se stranica sama osvezava dok ceka konacan status. */
const POLL_EVERY_MS = 3000;
const POLL_FOR_MS = 3 * 60 * 1000;

/**
 * P08 - stranica na koju PSP vraca kupca (SUCCESS_URL / FAILED_URL / ERROR_URL).
 * Prikaz zavisi iskljucivo od stanja naloga u parking sistemu (potvrda PSP-a), a ne od URL-a:
 * parametar "status" iz redirect-a sluzi samo kao nagovestaj dok potvrda ne stigne.
 */
@Component({
  selector: 'app-payment-result',
  imports: [RouterLink, Logo],
  templateUrl: './payment-result.html',
  styleUrl: './payment-result.scss',
})
export class PaymentResult implements OnInit {
  private readonly api = inject(ParkingApi);
  protected readonly auth = inject(AuthService);

  /** :ishod iz putanje (uspeh | neuspeh | greska) i query parametri koje dodaje PSP */
  readonly ishod = input<string>();
  readonly merchant_order_id = input<string>();
  readonly status = input<string>();

  protected readonly order = signal<OrderResponse | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly polling = signal(false);

  private timer: ReturnType<typeof setInterval> | null = null;
  private startedAt = Date.now();

  protected readonly view = computed<View>(() => {
    const o = this.order();
    if (!o) return this.loadError() ? 'error' : 'pending';
    const s = o.session.status;
    if (o.paymentStatus === 'SUCCESS' && s === 'ACTIVE') return 'success';
    if (s === 'EXPIRED' && o.paymentStatus === 'SUCCESS') return 'success';
    if (o.paymentStatus === 'ERROR') return 'error';
    if (o.paymentStatus === 'FAILED' || o.paymentStatus === 'EXPIRED' || s === 'CANCELLED') return 'failed';
    return 'pending';
  });

  /** PSP je preusmerio na FAILED/ERROR URL, a konacna potvrda jos nije stigla. */
  protected readonly redirectHint = computed(() => {
    if (this.view() !== 'pending') return null;
    const hint = (this.status() ?? this.ishod() ?? '').toLowerCase();
    if (['failed', 'neuspeh', 'expired'].includes(hint)) return 'PSP je prijavio da plaćanje nije uspelo.';
    if (['error', 'greska'].includes(hint)) return 'PSP je prijavio grešku u obradi plaćanja.';
    return null;
  });

  protected readonly retryParams = computed(() => {
    const s = this.order()?.session;
    return s ? { zona: s.zoneId, vozilo: s.vehicleId, trajanje: s.durationMinutes } : {};
  });

  protected readonly money = money;
  protected readonly time = time;
  protected readonly methodLabel = methodLabel;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  async ngOnInit() {
    const id = this.merchant_order_id();
    if (!id) {
      this.loadError.set('Nedostaje broj naloga. Status plaćanja možete proveriti na pregledu.');
      return;
    }
    await this.load(id);
    if (this.view() === 'pending') {
      this.polling.set(true);
      this.timer = setInterval(async () => {
        await this.load(id);
        if (this.view() !== 'pending' || Date.now() - this.startedAt > POLL_FOR_MS) this.stopPolling();
      }, POLL_EVERY_MS);
    }
  }

  private async load(id: string) {
    try {
      this.order.set(await this.api.order(id));
      this.loadError.set(null);
    } catch (e) {
      if (!this.order()) this.loadError.set(errorMessage(e));
    }
  }

  private stopPolling() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.polling.set(false);
  }
}
