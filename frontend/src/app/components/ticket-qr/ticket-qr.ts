import { Component, ElementRef, effect, input, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { toCanvas } from 'qrcode';

// A ticket's QR code, drawn in the browser (the code never goes to a third-party QR service). The same code is
// printed under it, for the door to type if a scan fails.
@Component({
  selector: 'app-ticket-qr',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <canvas #canvas [attr.aria-label]="label()" role="img"></canvas>
    <code>{{ grouped() }}</code>
  `,
  styles: `
    :host { display: inline-flex; flex-direction: column; align-items: center; gap: 6px; }
    canvas { border-radius: 8px; }
    code { font-size: 12px; letter-spacing: 0.04em; color: var(--asbl-slate-600); word-break: break-all; text-align: center; }
  `,
})
export class TicketQr {
  readonly code = input.required<string>();
  readonly label = input('');
  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    effect(() => {
      // Error correction M: still readable from a slightly damaged or glare-covered phone screen.
      void toCanvas(this.canvas().nativeElement, this.code(), { width: 192, margin: 1, errorCorrectionLevel: 'M' });
    });
  }

  // "0123 4567 89ab …": easier to read out or type at the door.
  grouped(): string {
    return this.code().replace(/(.{4})/g, '$1 ').trim();
  }
}
