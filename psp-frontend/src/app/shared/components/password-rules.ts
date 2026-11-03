import { Component, computed, input } from '@angular/core';

/** Pravila lozinke (PCI DSS 8.3.6 / 8.3.7) - ista kao provera na serveru. */
@Component({
  selector: 'app-password-rules',
  template: `
    <div class="rules">
      @for (rule of rules(); track rule.label) {
        <span class="rule" [class.pass]="rule.state === 'pass'" [class.fail]="rule.state === 'fail'">
          @if (rule.state === 'pass') {
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#1F6B46" stroke-width="2.4" aria-hidden="true"><path d="M5 12l5 5L20 7"></path></svg>
          } @else if (rule.state === 'fail') {
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#A4281F" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
          } @else {
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#857B66" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="8"></circle></svg>
          }
          {{ rule.label }}
        </span>
      }
    </div>
  `,
  styles: `
    .rules {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .rule {
      display: flex;
      gap: 10px;
      align-items: center;
      font-size: 13px;
      color: var(--text-3);
    }
    .rule.pass {
      color: var(--text);
    }
    .rule.fail {
      color: var(--err);
    }
  `,
})
export class PasswordRules {
  readonly password = input('');
  readonly confirmation = input('');
  /** Server je odbio lozinku jer je ista kao neka od poslednje 4. */
  readonly reused = input(false);

  protected readonly rules = computed(() => {
    const pw = this.password();
    const state = (ok: boolean) => (pw ? (ok ? 'pass' : 'fail') : 'idle');
    const classes = /\p{L}/u.test(pw) && /\d/.test(pw) && /[^\p{L}\d\s]/u.test(pw);
    return [
      { label: 'Najmanje 12 karaktera', state: state(pw.length >= 12) },
      { label: 'Slova, brojevi i simboli', state: state(classes) },
      {
        label: 'Lozinke se poklapaju',
        state: this.confirmation() ? (pw === this.confirmation() ? 'pass' : 'fail') : 'idle',
      },
      { label: 'Različita od poslednje 4', state: this.reused() ? 'fail' : 'idle' },
    ];
  });
}

export function passwordAcceptable(password: string, confirmation: string): boolean {
  return (
    password.length >= 12 &&
    /\p{L}/u.test(password) &&
    /\d/.test(password) &&
    /[^\p{L}\d\s]/u.test(password) &&
    password === confirmation
  );
}
