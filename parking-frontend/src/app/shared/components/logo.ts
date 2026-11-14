import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Znak "P" + naziv aplikacije. */
@Component({
  selector: 'app-logo',
  imports: [RouterLink],
  template: `
    <a routerLink="/pregled" class="logo" [class.dark]="dark()">
      <span class="mark">P</span><span class="name">SEP Parking</span>
    </a>
  `,
  styles: `
    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      color: #ffffff;
    }
    .logo:hover {
      color: #ffffff;
    }
    .mark {
      width: 28px;
      height: 28px;
      background: #ffffff;
      color: var(--navy);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 14px;
    }
    .name {
      font-weight: 600;
      letter-spacing: 0.02em;
    }
    .logo.dark,
    .logo.dark:hover {
      color: var(--navy);
    }
    .logo.dark .mark {
      width: 32px;
      height: 32px;
      font-size: 15px;
      background: var(--navy);
      color: #ffffff;
    }
  `,
})
export class Logo {
  readonly dark = input(false);
}
