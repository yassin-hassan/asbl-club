import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { switchMap, take, takeWhile, timer } from 'rxjs';
import { MyRegistration, RegistrationsService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

type Outcome = 'checking' | 'confirmed' | 'processing' | 'failed';

// Where Stripe sends the browser after a payment attempt. Stripe's redirect only *suggests* the outcome (anyone can
// type this URL); the truth is the booking's status on our server, set by Stripe's signed webhook. So: ask the
// server every 2 s, for up to 30 s, until the booking is no longer waiting for payment.
@Component({
  selector: 'app-payment-complete',
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
      takeWhile((registration) => registration.status === 'RESERVED', true),
    ).subscribe({
      next: (registration) => {
        this.registration.set(registration);
        this.outcome.set(registration.status === 'RESERVED' ? 'checking' : 'confirmed');
      },
      error: (err) => this.error.set(errorMessageKey(err)),
      complete: () => {
        if (this.registration()?.status === 'RESERVED') {
          this.outcome.set('processing'); // still waiting for Stripe's confirmation after 30 s
        }
      },
    });
  }
}
