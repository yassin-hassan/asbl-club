import { Component, input } from '@angular/core';

// The asbl.club logo: an indigo tile with three dots, and the word mark with an amber ".club".
@Component({
  selector: 'app-logo',
  template: `
    <span class="mark" aria-hidden="true"><span class="d1"></span><span class="d2"></span><span class="d3"></span></span>
    <span class="word" [class.inverse]="inverse()">asbl<b>.club</b></span>
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 11px; }
    .mark { width: 40px; height: 40px; border-radius: 12px; background: var(--asbl-indigo-600); position: relative; flex: none; }
    .mark span { position: absolute; width: 10px; height: 10px; border-radius: 999px; }
    .d1 { background: #fff; top: 10px; left: 10px; }
    .d2 { background: var(--asbl-amber-500); top: 10px; left: 21px; }
    .d3 { background: var(--asbl-indigo-100); top: 21px; left: 15px; }
    .word { font-family: var(--asbl-heading-font); font-weight: 700; font-size: 24px; color: var(--asbl-indigo-900); letter-spacing: -0.02em; }
    .word b { color: var(--asbl-amber-500); font-weight: 700; }
    .word.inverse { color: #fff; }
  `,
})
export class Logo {
  readonly inverse = input(false);
}
