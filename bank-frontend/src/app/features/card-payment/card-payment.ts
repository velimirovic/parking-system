import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import {
  checkExpiry,
  detectBrand,
  digitsOnly,
  formatExpiry,
  formatPan,
  validHolder,
  validPan,
  validSecurityCode,
} from '../../core/card-rules';
import { amount, PaymentApi, PaymentFormView, PaymentResult, problemOf } from '../../core/payment-api';

const REDIRECT_DELAY_MS = 3000;

/**
 * B01 - stranica banke za placanje karticom (PAYMENT_URL).
 *
 * Forma je vezana za jednu transakciju, vremenski ogranicena i dozvoljava jedan pokusaj; iznos dolazi
 * od banke i ne moze se menjati. PAN se proverava Lunovom formulom, datum je MM/YY. Podaci kartice
 * se salju samo banci, ne cuvaju se u pregledacu i brisu se iz forme posle slanja.
 */
@Component({
  selector: 'app-card-payment',
  templateUrl: './card-payment.html',
  styleUrl: './card-payment.scss',
})
export class CardPayment implements OnInit {
  private readonly api = inject(PaymentApi);

  readonly paymentId = input.required<string>();

  protected readonly view = signal<PaymentFormView | null>(null);
  protected readonly notFound = signal(false);
  protected readonly loadError = signal<string | null>(null);

  protected readonly pan = signal('');
  protected readonly holder = signal('');
  protected readonly expiry = signal('');
  protected readonly cvv = signal('');
  protected readonly touched = signal({ pan: false, holder: false, expiry: false, cvv: false });

  protected readonly busy = signal(false);
  protected readonly result = signal<PaymentResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly serverFields = signal<Record<string, string>>({});
  /** Forma vise ne prima unos (iskoriscen pokusaj, istekla, zavrsena) - kupac se vraca na PSP. */
  protected readonly closedUrl = signal<string | null>(null);
  protected readonly closedText = signal<string | null>(null);

  private readonly now = signal(Date.now());
  private clockOffset = 0;

  protected readonly amount = amount;

  protected readonly digits = computed(() => digitsOnly(this.pan()));
  protected readonly brand = computed(() => detectBrand(this.digits()));
  protected readonly panOk = computed(() => validPan(this.digits()) && this.brand() !== null);
  protected readonly panMessage = computed(() => {
    const d = this.digits();
    if (this.serverFields()['pan']) return this.serverFields()['pan'];
    if (!d) return 'Unesite 13–19 cifara';
    if (!validPan(d)) return d.length < 13 ? 'Unesite 13–19 cifara' : 'Neispravan broj kartice';
    if (!this.brand()) return 'Banka ne prihvata ovu vrstu kartice';
    return 'Broj kartice je ispravan (Luhn)';
  });
  protected readonly expiryCheck = computed(() => checkExpiry(this.expiry()));
  protected readonly holderOk = computed(() => validHolder(this.holder()));
  protected readonly cvvOk = computed(() => validSecurityCode(this.cvv()));

  protected readonly secondsLeft = computed(() => {
    const v = this.view();
    if (!v) return 0;
    return Math.max(0, Math.floor((Date.parse(v.expiresAt) - (this.now() + this.clockOffset)) / 1000));
  });
  protected readonly left = computed(() => {
    const s = this.secondsLeft();
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  });

  protected readonly canPay = computed(
    () =>
      this.panOk() &&
      this.expiryCheck().ok &&
      this.holderOk() &&
      this.cvvOk() &&
      this.secondsLeft() > 0 &&
      !this.busy() &&
      !this.result() &&
      !this.closedUrl(),
  );

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  async ngOnInit() {
    try {
      const v = await this.api.view(this.paymentId());
      this.clockOffset = Date.parse(v.serverTime) - Date.now();
      this.view.set(v);
      if (v.status !== 'CREATED' || v.attemptUsed) {
        this.close(v.resultUrl, v.status === 'EXPIRED'
          ? 'Vreme za plaćanje je isteklo.'
          : 'Ova forma je već iskorišćena. Dozvoljen je samo jedan pokušaj plaćanja.');
      }
    } catch (e) {
      const problem = problemOf(e);
      if (problem.status === 404) this.notFound.set(true);
      else this.loadError.set(problem.detail);
    }
  }

  protected onPan(event: Event) {
    const input = event.target as HTMLInputElement;
    const formatted = formatPan(digitsOnly(input.value).slice(0, 19));
    this.pan.set(formatted);
    input.value = formatted;
    this.clearServerField('pan');
  }

  protected onExpiry(event: Event) {
    const input = event.target as HTMLInputElement;
    const formatted = formatExpiry(input.value);
    this.expiry.set(formatted);
    input.value = formatted;
    this.clearServerField('expiry');
  }

  protected onCvv(event: Event) {
    const input = event.target as HTMLInputElement;
    const value = digitsOnly(input.value).slice(0, 3);
    this.cvv.set(value);
    input.value = value;
  }

  protected onHolder(event: Event) {
    this.holder.set((event.target as HTMLInputElement).value.toUpperCase());
  }

  protected touch(field: 'pan' | 'holder' | 'expiry' | 'cvv') {
    this.touched.update((t) => ({ ...t, [field]: true }));
  }

  protected async pay() {
    if (!this.canPay()) {
      this.touched.set({ pan: true, holder: true, expiry: true, cvv: true });
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const result = await this.api.pay(this.paymentId(), {
        pan: this.digits(),
        securityCode: this.cvv(),
        cardHolderName: this.holder().trim(),
        expiry: this.expiry(),
      });
      this.wipeCard();
      this.result.set(result);
      setTimeout(() => window.location.assign(result.redirectUrl), REDIRECT_DELAY_MS);
    } catch (e) {
      const problem = problemOf(e);
      if (problem.code === 'VALIDATION_FAILED' && problem.fields) {
        // neispravan format ne trosi pokusaj - kupac moze da ispravi unos
        this.serverFields.set(problem.fields);
        this.error.set('Proverite podatke kartice.');
      } else if (problem.code === 'ATTEMPT_USED' || problem.code === 'PAYMENT_EXPIRED') {
        this.wipeCard();
        this.close(problem.resultUrl ?? null, problem.detail);
      } else {
        this.error.set(problem.detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async cancel() {
    if (this.busy() || this.result()) return;
    const back = this.closedUrl();
    if (back) {
      window.location.assign(back);
      return;
    }
    this.busy.set(true);
    try {
      const result = await this.api.cancel(this.paymentId());
      this.wipeCard();
      window.location.assign(result.redirectUrl);
    } catch (e) {
      const problem = problemOf(e);
      if (problem.resultUrl) window.location.assign(problem.resultUrl);
      else {
        this.error.set(problem.detail);
        this.busy.set(false);
      }
    }
  }

  protected returnNow() {
    const url = this.result()?.redirectUrl ?? this.closedUrl();
    if (url) window.location.assign(url);
  }

  private close(url: string | null, text: string) {
    this.closedUrl.set(url);
    this.closedText.set(text);
  }

  private clearServerField(field: string) {
    if (this.serverFields()[field]) {
      const { [field]: _, ...rest } = this.serverFields();
      this.serverFields.set(rest);
    }
  }

  /** Podaci kartice se ne zadrzavaju u memoriji stranice posle slanja. */
  private wipeCard() {
    this.pan.set('');
    this.cvv.set('');
    this.expiry.set('');
    this.holder.set('');
  }
}
