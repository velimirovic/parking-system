import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { CheckoutView } from '../../../core/models';
import { CheckoutApiService } from '../../../core/services/checkout-api.service';
import { errorMessage, money, problemOf } from '../../../shared/format';
import { isFinal, outcome } from '../checkout-meta';

const POLL_MS = 2000;
const POLL_LIMIT_MS = 120_000;
const REDIRECT_DELAY_MS = 3000;

/**
 * Povratak kupca sa stranice banke (ili drugog plugin-a). Konacan status ne dolazi iz URL-a nego
 * iskljucivo od PSP-a, pa stranica proverava status dok ne postane konacan i zatim vraca kupca
 * na SUCCESS / FAILED / ERROR URL klijenta.
 */
@Component({
  selector: 'app-checkout-result',
  templateUrl: './checkout-result.html',
  styleUrl: '../checkout.scss',
})
export class CheckoutResult implements OnInit {
  private readonly api = inject(CheckoutApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly view = signal<CheckoutView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);
  protected readonly gaveUp = signal(false);
  protected readonly money = money;

  protected readonly final = computed(() => {
    const v = this.view();
    return v && isFinal(v.status) ? outcome(v) : null;
  });

  private started = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit() {
    this.started = Date.now();
    this.poll();
  }

  protected retry() {
    this.gaveUp.set(false);
    this.error.set(null);
    this.started = Date.now();
    this.poll();
  }

  protected returnNow() {
    const url = this.view()?.returnUrl;
    if (url) window.location.assign(url);
  }

  private async poll() {
    try {
      const v = await this.api.view(this.id());
      this.view.set(v);
      this.error.set(null);
      if (isFinal(v.status)) {
        if (v.returnUrl) this.timer = setTimeout(() => window.location.assign(v.returnUrl!), REDIRECT_DELAY_MS);
        return;
      }
    } catch (e) {
      if (problemOf(e).status === 404) {
        this.notFound.set(true);
        return;
      }
      this.error.set(errorMessage(e));
    }
    if (Date.now() - this.started > POLL_LIMIT_MS) {
      this.gaveUp.set(true);
      return;
    }
    this.timer = setTimeout(() => this.poll(), POLL_MS);
  }
}
