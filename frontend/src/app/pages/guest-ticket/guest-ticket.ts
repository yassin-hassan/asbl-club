import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, Observable, switchMap, take, takeWhile, timer } from 'rxjs';
import { GuestBooking, GuestBookingsService } from '../../api/generated';
import { ConfirmDialog } from '../../components/confirm-dialog/confirm-dialog';
import { TicketQr } from '../../components/ticket-qr/ticket-qr';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';
import { cancelDialog, cancellationErrorKey } from '../my-bookings/my-bookings';
import { WAITING } from '../payment-complete/payment-complete';

// A guest's booking (bought without an account), opened through its secret link: /tickets/:token. From here they
// pay, and once Stripe's signed webhook has confirmed the payment, they find their ticket (the QR code, also emailed).
// Back from Stripe, the server is asked every 2 s, for up to 30 s, until the booking has its outcome.
@Component({
  selector: 'app-guest-ticket',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, TicketQr],
  templateUrl: './guest-ticket.html',
  styles: '.ticket { display: flex; justify-content: center; margin: 20px 0; }',
})
export class GuestTicket {
  private route = inject(ActivatedRoute);
  readonly lang = inject(LanguageService).active;
  readonly token = this.route.snapshot.paramMap.get('token') ?? '';
  private readonly redirect = this.route.snapshot.queryParamMap.get('redirect_status');

  private api = inject(GuestBookingsService);
  private dialog = inject(MatDialog);
  readonly booking = signal<GuestBooking | null>(null);
  readonly cancelling = signal(false);
  readonly cancelError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly checking = signal(false);
  // Stripe said the payment failed (a declined card): the booking stays reserved, they can try again.
  readonly failed = this.redirect === 'failed';
  // Back from Stripe's form after paying: the outcome is awaited from the server.
  readonly backFromPaying = this.redirect !== null && !this.failed;

  constructor() {
    const api = this.api;
    const backFromPaying = this.backFromPaying;
    const load: Observable<GuestBooking> = backFromPaying
      ? timer(0, 2000).pipe(
          take(15),
          switchMap(() => api.getGuestBooking(this.token)),
          takeWhile((booking) => WAITING.has(booking.status), true),
        )
      : api.getGuestBooking(this.token);
    this.checking.set(backFromPaying);
    load.subscribe({
      next: (booking) => {
        this.booking.set(booking);
        this.checking.set(backFromPaying && WAITING.has(booking.status));
      },
      error: (err: HttpErrorResponse) =>
        this.error.set(err.status === 404 ? 'guestTicket.notFound' : errorMessageKey(err)),
      complete: () => this.checking.set(false),
    });
  }

  // Give up the unpaid booking (its seat freed at once), or cancel the paid ticket (within the event's cancellation
  // delay) and be refunded.
  cancel(booking: GuestBooking): void {
    const data = cancelDialog(booking.status === 'RESERVED', booking.eventTitle);
    this.dialog.open(ConfirmDialog, { data }).afterClosed().pipe(
      filter((confirmed) => confirmed === true),
      switchMap(() => {
        this.cancelling.set(true);
        this.cancelError.set(null);
        return this.api.cancelGuestBooking(this.token);
      }),
    ).subscribe({
      next: (cancelled) => {
        this.cancelling.set(false);
        this.booking.set(cancelled);
      },
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(false);
        this.cancelError.set(cancellationErrorKey(err));
      },
    });
  }
}
