import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { Logo } from './logo';

/** Okvir aplikacije: tamno zaglavlje sa navigacijom (P02-P06). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Logo],
  template: `
    <div class="shell">
      <header class="top">
        <div class="top-in">
          <app-logo />
          <nav class="nav-list" aria-label="Glavna navigacija">
            <a class="nav" routerLink="/pregled" routerLinkActive="on">Pregled</a>
            <a class="nav" routerLink="/nova-sesija" routerLinkActive="on">Nova sesija</a>
            <a class="nav" routerLink="/zone" routerLinkActive="on">Zone</a>
            <a class="nav" routerLink="/istorija" routerLinkActive="on">Istorija</a>
            <a class="nav" routerLink="/vozila" routerLinkActive="on">Vozila i obaveštenja</a>
          </nav>
          <div class="user">
            <button
              type="button"
              class="avatar"
              [attr.aria-expanded]="menuOpen()"
              aria-label="Nalog"
              (click)="menuOpen.set(!menuOpen())"
            >
              {{ auth.user()?.initials }}
            </button>
            @if (menuOpen()) {
              <div class="menu" role="menu">
                <span class="menu-name">{{ auth.user()?.firstName }} {{ auth.user()?.lastName }}</span>
                <span class="menu-mail">{{ auth.user()?.email }}</span>
                <button type="button" class="menu-item" role="menuitem" (click)="auth.logout()">Odjava</button>
              </div>
            }
          </div>
        </div>
      </header>
      <router-outlet />
    </div>
  `,
  styles: `
    .shell {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .top {
      background: var(--navy);
    }
    .top-in {
      max-width: 1200px;
      margin: 0 auto;
      width: 100%;
      padding: 0 32px;
      min-height: 56px;
      display: flex;
      align-items: center;
      gap: 48px;
      flex-wrap: wrap;
    }
    .nav-list {
      display: flex;
      gap: 32px;
      flex-wrap: wrap;
    }
    .nav {
      color: var(--navy-muted);
      font-size: 14px;
      padding: 18px 0 16px;
      border-bottom: 2px solid transparent;
    }
    .nav:hover {
      color: #ffffff;
    }
    .nav.on {
      color: #ffffff;
      border-bottom-color: #ffffff;
      font-weight: 500;
    }
    .user {
      margin-left: auto;
      position: relative;
    }
    .avatar {
      width: 34px;
      height: 34px;
      border: 1px solid #ffffff;
      background: transparent;
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
    }
    .menu {
      position: absolute;
      right: 0;
      top: 44px;
      z-index: 10;
      min-width: 220px;
      background: var(--surface);
      border: 1px solid var(--navy);
      box-shadow: 0 10px 24px rgba(19, 38, 74, 0.12);
      display: flex;
      flex-direction: column;
      padding: 12px 0 0;
    }
    .menu-name {
      padding: 0 16px;
      font-weight: 600;
    }
    .menu-mail {
      padding: 2px 16px 12px;
      font-size: 12px;
      color: var(--text-3);
      border-bottom: 1px solid var(--line-soft);
    }
    .menu-item {
      text-align: left;
      height: 40px;
      padding: 0 16px;
      border: 0;
      background: transparent;
      color: var(--err);
      cursor: pointer;
    }
    .menu-item:hover {
      background: var(--surface-alt);
    }
    @media (max-width: 720px) {
      .top-in {
        padding: 0 16px;
        gap: 16px;
      }
      .nav-list {
        gap: 18px;
        order: 3;
        width: 100%;
        overflow-x: auto;
      }
    }
  `,
})
export class AppShell {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
}
