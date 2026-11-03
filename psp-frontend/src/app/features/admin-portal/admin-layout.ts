import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AdminCounts } from './admin-counts';

/** Okvir administratorskog portala (S07). */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="side">
        <span class="side-brand">
          <span class="wordmark">Maluvufy <em>Pay</em></span><span class="tag">ADMIN</span>
        </span>
        <nav aria-label="Administratorski portal">
          <a class="sn" routerLink="klijenti" routerLinkActive="on">
            Klijenti<span class="mono" style="font-size: 12px">{{ counts.merchants() ?? '' }}</span>
          </a>
          <a class="sn" routerLink="plugini" routerLinkActive="on">
            Plugin-ovi<span class="mono" style="font-size: 12px">{{ counts.plugins() ?? '' }}</span>
          </a>
          <a class="sn" routerLink="audit" routerLinkActive="on">
            Audit log<span class="mono" style="font-size: 12px">{{ counts.audit() ?? '' }}</span>
          </a>
          <a class="sn" routerLink="bezbednost" routerLinkActive="on">Bezbednost naloga</a>
        </nav>
        <div class="side-user">
          <span style="font-weight: 500">{{ user()?.fullName }}</span>
          <span class="role">Super admin · MFA {{ user()?.mfaEnabled ? 'uključen' : 'isključen' }}</span>
          <button type="button" class="logout" (click)="auth.logout()">Odjava</button>
        </div>
      </aside>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  protected readonly counts = inject(AdminCounts);
  protected readonly user = this.auth.user;

  constructor() {
    this.counts.refresh();
  }
}
