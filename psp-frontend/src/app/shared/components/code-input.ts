import { Component, ElementRef, input, output, signal, viewChildren } from '@angular/core';

/**
 * Sest polja za jednokratni kod iz Authenticator aplikacije: automatski prelaz na sledece polje,
 * Backspace na prethodno, lepljenje celog koda.
 */
@Component({
  selector: 'app-code-input',
  template: `
    <div class="row" role="group" aria-label="Šestocifreni kod">
      @for (digit of digits(); track $index) {
        <input
          #box
          class="d"
          inputmode="numeric"
          autocomplete="one-time-code"
          [value]="digit"
          [disabled]="disabled()"
          [attr.aria-label]="'Cifra ' + ($index + 1)"
          (input)="onInput($index, $event)"
          (keydown)="onKeydown($index, $event)"
          (paste)="onPaste($event)"
        />
      }
    </div>
  `,
  styles: `
    .row {
      display: grid;
      grid-template-columns: repeat(6, minmax(0, 1fr));
      gap: 6px;
    }
    .d {
      width: 100%;
      height: 52px;
      border: 1px solid var(--line-input);
      border-radius: 0;
      background: var(--surface);
      font-family: var(--mono);
      font-size: 22px;
      text-align: center;
      color: var(--text);
    }
  `,
})
export class CodeInput {
  readonly disabled = input(false);
  readonly completed = output<string>();

  protected readonly digits = signal<string[]>(['', '', '', '', '', '']);
  private readonly boxes = viewChildren<ElementRef<HTMLInputElement>>('box');

  value(): string {
    return this.digits().join('');
  }

  clear() {
    this.digits.set(['', '', '', '', '', '']);
    setTimeout(() => this.focus(0));
  }

  focus(index = 0) {
    this.boxes()[index]?.nativeElement.focus();
  }

  protected onInput(index: number, event: Event) {
    const target = event.target as HTMLInputElement;
    const typed = target.value.replace(/\D/g, '');
    // vise cifara odjednom (brzo kucanje, autofill) se rasporedjuje u sledeca polja
    const chars = typed.length > 1 && typed[0] === this.digits()[index] ? typed.slice(1) : typed;
    const fill = chars.slice(0, 6 - index).split('');
    target.value = fill[0] ?? '';
    if (fill.length === 0) {
      this.set(index, '');
      return;
    }
    fill.forEach((digit, offset) => this.set(index + offset, digit));
    this.focus(Math.min(index + fill.length, 5));
    this.emitIfComplete();
  }

  protected onKeydown(index: number, event: KeyboardEvent) {
    if (event.key === 'Backspace' && !this.digits()[index] && index > 0) {
      this.set(index - 1, '');
      this.focus(index - 1);
    }
  }

  protected onPaste(event: ClipboardEvent) {
    const code = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '').slice(0, 6);
    if (!code) return;
    event.preventDefault();
    this.digits.set([...code.padEnd(6, ' ')].map((c) => c.trim()));
    this.focus(Math.min(code.length, 5));
    this.emitIfComplete();
  }

  private set(index: number, value: string) {
    this.digits.update((d) => d.map((v, i) => (i === index ? value : v)));
  }

  private emitIfComplete() {
    const code = this.digits().join('');
    if (code.length === 6) this.completed.emit(code);
  }
}
