import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { switchMap, take, takeWhile, timer } from 'rxjs';
import { MyRegistration, RegistrationsService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

export type Outcome = 'checking' | 'confirmed' | 'refunded' | 'processing' | 'failed';

// Statuses in which Stripe's answer is still awaited. CANCELLED and EXPIRED too: the event was cancelled, or the
// booking ran out of time, while the person was paying; a payment that went through is refunded (ends as REFUNDED).
const WAITING = new Set(['RESERVED', 'CANCELLED', 'EXPIRED']);

// Where Stripe sends the browser after a payment attempt. Stripe's redirect only *suggests* the outcome (anyone can
// type this URL); the truth is the booking's status on our server, set by Stripe's signed webhook. So: ask the
// server every 2 s, for up to 30 s, until the booking has its outcome: paid, or refunded (event cancelled meanwhile).
@Component({
  selector: 'app-payment-complete',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './payment-complete.html',
  styles: '.narrow { max-width: 480px; margin: 0 auto; }',
})
export class PaymentComplete {
  private route = inject(ActivatedRoute);
  readonly registrationId = Number(this.route.snapshot.paramMap.get('id'));
  readonly outcome = signal<Outcome>('checking');
  readonly registration = signal<MyRegistration | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    if (this.route.snapshot.queryParamMap.get('redirect_status') === 'failed') {
      this.outcome.set('failed');
      return;
    }
    const api = inject(RegistrationsService);
    timer(0, 2000).pipe(
      take(15),
      switchMap(() => api.getMyRegistration(this.registrationId)),
      takeWhile((registration) => WAITING.has(registration.status), true),
    ).subscribe({
      next: (registration) => {
        this.registration.set(registration);
        this.outcome.set(outcomeOf(registration.status));
      },
      error: (err) => this.error.set(errorMessageKey(err)),
      complete: () => {
        if (WAITING.has(this.registration()?.status ?? '')) {
          this.outcome.set('processing'); // still waiting for Stripe's confirmation after 30 s
        }
      },
    });
  }
}

export function outcomeOf(status: string): Outcome {
  if (WAITING.has(status)) {
    return 'checking';
  }
  return status === 'REFUNDED' ? 'refunded' : 'confirmed';
}
