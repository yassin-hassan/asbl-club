import { Component, ElementRef, inject, signal, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DOCUMENT } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { loadStripe, Stripe, StripeElements } from '@stripe/stripe-js';
import { Checkout, DuesService, GuestBookingsService, RegistrationsService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

// Paying with Stripe's own payment form (cards, Bancontact…), for a booking (/pay/:id), a guest's booking
// (/tickets/:token/pay, no account) or this year's dues (/asbls/:slug/dues/pay). The form runs in Stripe's iframe: card details go from the browser straight to Stripe
// and never reach our server.
@Component({
  selector: 'app-checkout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './checkout.html',
  styles: '.narrow { max-width: 480px; margin: 0 auto; } .amount { font: var(--mat-sys-headline-small); }',
})
export class CheckoutPage {
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;
  private readonly params = inject(ActivatedRoute).snapshot.paramMap;
  // Dues when the address names an association, a guest's booking when it holds its secret link, otherwise a booking.
  readonly duesOf = this.params.get('slug');
  readonly guestToken = this.params.get('token');
  readonly registrationId = Number(this.params.get('id'));

  private readonly paymentElement = viewChild.required<ElementRef<HTMLElement>>('paymentElement');
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;

  readonly checkout = signal<Checkout | null>(null);
  readonly ready = signal(false);
  readonly paying = signal(false);
  readonly error = signal<string | null>(null);
  readonly stripeError = signal<string | null>(null); // Stripe's own message, already in the right language

  constructor() {
    const start = this.duesOf
      ? inject(DuesService).startDuesCheckout(this.duesOf)
      : this.guestToken
        ? inject(GuestBookingsService).startGuestCheckout(this.guestToken)
        : inject(RegistrationsService).startCheckout(this.registrationId);
    start.subscribe({
      next: (checkout) => {
        this.checkout.set(checkout);
        this.mountPaymentForm(checkout);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.checkoutErrorKey(err)),
    });
  }

  private async mountPaymentForm(checkout: Checkout): Promise<void> {
    // Payments go straight to the association's own Stripe account (a "direct charge").
    this.stripe = await loadStripe(checkout.publishableKey, { stripeAccount: checkout.stripeAccount });
    if (!this.stripe) {
      this.error.set('payment.providerDown');
      return;
    }
    this.elements = this.stripe.elements({ clientSecret: checkout.clientSecret, locale: this.lang() });
    const form = this.elements.create('payment');
    form.on('ready', () => this.ready.set(true));
    form.mount(this.paymentElement().nativeElement);
  }

  async pay(): Promise<void> {
    if (!this.stripe || !this.elements) {
      return;
    }
    this.paying.set(true);
    this.stripeError.set(null);
    // On success Stripe sends the browser to the "complete" page. That redirect proves nothing by itself: the
    // payment counts only once Stripe's signed webhook reaches the server.
    const complete = this.duesOf
      ? `/asbls/${this.duesOf}/dues/paid`
      : this.guestToken
        ? `/tickets/${this.guestToken}`
        : `/pay/${this.registrationId}/complete`;
    const { error } = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: { return_url: `${this.document.location.origin}${complete}` },
    });
    this.paying.set(false);
    if (error) {
      this.stripeError.set(error.message ?? null);
    }
  }

  private checkoutErrorKey(err: HttpErrorResponse): string {
    if (err.status === 409) {
      switch (problemOf(err)?.code) {
        case 'PAYMENTS_DISABLED':
          return 'payment.paymentsDisabled';
        case 'BOOKING_EXPIRED':
          return 'payment.expired';
        case 'ALREADY_PAID':
          return 'dues.alreadyPaid';
        case 'NO_DUES':
          return 'dues.none';
        default:
          return 'payment.notPayable';
      }
    }
    return err.status === 502 ? 'payment.providerDown' : errorMessageKey(err);
  }
}
