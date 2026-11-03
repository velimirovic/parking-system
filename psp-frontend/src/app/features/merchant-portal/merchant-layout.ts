import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/** Okvir klijentskog portala (S05, S06): podaci o klijentu, navigacija, prijavljeni korisnik. */
@Component({
  selector: 'app-merchant-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="side">
        <span class="side-brand"><span class="wordmark">Maluvufy <em>Pay</em></span></span>
        <div class="side-merchant">
          <span class="lbl" style="color: var(--navy-muted)">Klijent</span>
          <span style="font-weight: 600; font-size: 14px">{{ user()?.merchantName }}</span>
          <span class="mono" style="font-size: 12px; color: var(--navy-muted)">{{ user()?.merchantCode }}</span>
        </div>
        <nav aria-label="Klijentski portal">
          <a class="sn" routerLink="transakcije" routerLinkActive="on">Transakcije</a>
          <a class="sn" routerLink="metode" routerLinkActive="on">Metode i integracija</a>
          <a class="sn" routerLink="bezbednost" routerLinkActive="on">Bezbednost naloga</a>
        </nav>
        <div class="side-user">
          <span style="font-weight: 500">{{ user()?.fullName }}</span>
          <span class="role">Admin klijenta · MFA {{ user()?.mfaEnabled ? 'uključen' : 'isključen' }}</span>
          <button type="button" class="logout" (click)="auth.logout()">Odjava</button>
        </div>
      </aside>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
})
export class MerchantLayout {
  protected readonly auth = inject(AuthService);
  protected readonly user = this.auth.user;
}
