import { Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  template: `
    <div class="bk">
      <header class="bk-top">
        <div class="bk-top-in">
          <span class="bk-brand"><span class="bk-logo">B</span><span style="font-weight: 600">SEP Banka</span></span>
        </div>
      </header>
      <main class="bk-main">
        <div style="max-width: 560px; background: var(--surface); border: 1px solid var(--line); padding: 32px">
          <h1 style="margin: 0 0 12px; font-size: 24px">Stranica ne postoji</h1>
          <p style="margin: 0; color: var(--text-2); line-height: 1.6">
            Plaćanje karticom se otvara isključivo preko linka koji dobijete od prodavca.
          </p>
        </div>
      </main>
    </div>
  `,
})
export class NotFound {}
