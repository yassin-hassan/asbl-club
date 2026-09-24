import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DOCUMENT } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { loadStripe, Stripe, StripeElements } from '@stripe/stripe-js';
import { Checkout, RegistrationsService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

// Paying for a booking with Stripe's own payment form (cards, Bancontact…). The form runs in Stripe's iframe:
// card details go from the browser straight to Stripe and never reach our server.
@Component({
  selector: 'app-checkout',
  imports: [CurrencyPipe, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './checkout.html',
  styles: '.narrow { max-width: 480px; margin: 0 auto; } .amount { font: var(--mat-sys-headline-small); }',
})
export class CheckoutPage {
  private api = inject(RegistrationsService);
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;
  readonly registrationId = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  private readonly paymentElement = viewChild.required<ElementRef<HTMLElement>>('paymentElement');
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;

  readonly checkout = signal<Checkout | null>(null);
  readonly ready = signal(false);
  readonly paying = signal(false);
  readonly error = signal<string | null>(null);
  readonly stripeError = signal<string | null>(null); // Stripe's own message, already in the right language

  constructor() {
    this.api.startCheckout(this.registrationId).subscribe({
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
    // booking is marked paid only when Stripe's signed webhook reaches the server.
    const { error } = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: { return_url: `${this.document.location.origin}/pay/${this.registrationId}/complete` },
    });
    this.paying.set(false);
    if (error) {
      this.stripeError.set(error.message ?? null);
    }
  }

  private checkoutErrorKey(err: HttpErrorResponse): string {
    if (err.status === 409) {
      return problemOf(err)?.code === 'PAYMENTS_DISABLED' ? 'payment.paymentsDisabled' : 'payment.notPayable';
    }
    return err.status === 502 ? 'payment.providerDown' : errorMessageKey(err);
  }
}
