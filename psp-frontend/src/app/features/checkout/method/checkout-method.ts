import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { CheckoutView } from '../../../core/models';
import { CheckoutApiService } from '../../../core/services/checkout-api.service';
import { errorMessage, money, problemOf } from '../../../shared/format';
import { countdown, isFinal, methodMeta, outcome } from '../checkout-meta';

/**
 * S01 - izbor nacina placanja. Kupac ovde dolazi preusmeren od klijenta (parking sistem),
 * bira jednu od metoda koje je klijent aktivirao i PSP ga salje dalje (npr. na stranicu banke).
 */
@Component({
  selector: 'app-checkout-method',
  templateUrl: './checkout-method.html',
  styleUrl: '../checkout.scss',
})
export class CheckoutMethod implements OnInit {
  private readonly api = inject(CheckoutApiService);

  /** ID transakcije iz rute /placanje/:id */
  readonly id = input.required<string>();

  protected readonly view = signal<CheckoutView | null>(null);
  protected readonly selected = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);
  protected readonly now = signal(Date.now());
  private clockOffset = 0;

  protected readonly money = money;
  protected readonly meta = methodMeta;

  protected readonly left = computed(() => countdown(this.view(), this.clockOffset, this.now()));
  protected readonly final = computed(() => {
    const v = this.view();
    return v && isFinal(v.status) ? outcome(v) : null;
  });
  protected readonly cta = computed(() => {
    const code = this.selected();
    return code ? methodMeta(code).cta : 'Izaberite način plaćanja';
  });
  protected readonly timeUp = computed(() => this.left() === '00:00');

  constructor() {
    const timer = setInterval(() => {
      this.now.set(Date.now());
      const v = this.view();
      // posle isteka roka server zatvara transakciju - osvezi prikaz jednom
      if (v?.status === 'INIT' && this.timeUp() && !this.busy()) this.load();
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit() {
    this.load();
  }

  protected async load() {
    try {
      const v = await this.api.view(this.id());
      this.clockOffset = Date.parse(v.serverTime) - Date.now();
      this.view.set(v);
      if (!this.selected() && v.methods.length > 0) this.selected.set(v.methods[0].code);
      if (isFinal(v.status) && v.returnUrl) this.leaveTo(v.returnUrl, 3000);
    } catch (e) {
      if (problemOf(e).status === 404) this.notFound.set(true);
      else this.error.set(errorMessage(e));
    }
  }

  protected pick(code: string) {
    if (!this.busy()) this.selected.set(code);
  }

  /** Strelicama se menja izbor u grupi (role="radiogroup"). */
  protected keyNav(event: KeyboardEvent, index: number) {
    const methods = this.view()?.methods ?? [];
    const step = event.key === 'ArrowDown' || event.key === 'ArrowRight' ? 1
      : event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? -1 : 0;
    if (!step || methods.length === 0) return;
    event.preventDefault();
    const next = (index + step + methods.length) % methods.length;
    this.pick(methods[next].code);
    const buttons = (event.currentTarget as HTMLElement).parentElement?.querySelectorAll('button');
    (buttons?.[next] as HTMLButtonElement | undefined)?.focus();
  }

  protected async proceed() {
    const code = this.selected();
    if (!code || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      const { redirectUrl } = await this.api.selectMethod(this.id(), code);
      window.location.assign(redirectUrl);
    } catch (e) {
      const problem = problemOf(e);
      this.error.set(errorMessage(e));
      this.busy.set(false);
      if (problem.code === 'TRANSACTION_CLOSED' || problem.code === 'METHOD_ALREADY_SELECTED') {
        await this.load();
      }
    }
  }

  protected continuePayment() {
    const url = this.view()?.continueUrl;
    if (url) window.location.assign(url);
  }

  protected async cancel() {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const v = await this.api.cancel(this.id());
      this.view.set(v);
      if (v.returnUrl) this.leaveTo(v.returnUrl, 0);
      else this.busy.set(false);
    } catch (e) {
      this.error.set(errorMessage(e));
      this.busy.set(false);
    }
  }

  protected returnNow() {
    const url = this.view()?.returnUrl;
    if (url) window.location.assign(url);
  }

  private leaveTo(url: string, delayMs: number) {
    setTimeout(() => window.location.assign(url), delayMs);
  }
}
