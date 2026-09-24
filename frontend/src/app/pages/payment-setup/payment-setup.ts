import { Component, DOCUMENT, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { PaymentSetupService, PaymentSetupStatus } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

// Connecting the association to Stripe (administrators). The onboarding itself happens on Stripe's own pages:
// we ask the API for a one-time link, leave the app, and Stripe sends the administrator back here.
// Coming back with ?resume means that link had expired: we ask for a fresh one straight away.
@Component({
  selector: 'app-payment-setup',
  imports: [RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './payment-setup.html',
})
export class PaymentSetup {
  private readonly api = inject(PaymentSetupService);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);
  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly setup = signal<PaymentSetupStatus | null>(null);
  readonly redirecting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    if (this.route.snapshot.queryParamMap.has('resume')) {
      this.goToStripe();
    } else {
      this.api.getPaymentSetup(this.slug).subscribe({
        next: (setup) => this.setup.set(setup),
        error: (err: HttpErrorResponse) => this.error.set(this.messageFor(err)),
      });
    }
  }

  goToStripe(): void {
    this.redirecting.set(true);
    this.error.set(null);
    this.api.startStripeOnboarding(this.slug).subscribe({
      next: ({ url }) => {
        // Only ever leave the app for Stripe's own onboarding site.
        if (new URL(url).origin === 'https://connect.stripe.com') {
          this.document.location.href = url;
        } else {
          this.fail('errors.generic');
        }
      },
      error: (err: HttpErrorResponse) => this.fail(this.messageFor(err)),
    });
  }

  private fail(message: string): void {
    this.redirecting.set(false);
    this.error.set(message);
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 403) return 'payments.adminsOnly';
    if (err.status === 502) return 'payments.stripeDown';
    return errorMessageKey(err);
  }
}
