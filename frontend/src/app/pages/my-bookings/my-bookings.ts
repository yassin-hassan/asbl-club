import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, switchMap } from 'rxjs';
import { MyBooking, RegistrationsService } from '../../api/generated';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { TicketQr } from '../../components/ticket-qr/ticket-qr';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

// Splits bookings into events still to come and events that are over (an event counts as upcoming the whole day
// it happens, so a ticket doesn't disappear at the door). Soonest upcoming first, most recent past first.
export function splitByDate(bookings: MyBooking[], now: Date): { upcoming: MyBooking[]; past: MyBooking[] } {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const upcoming = bookings.filter((b) => new Date(b.startsAt).getTime() >= startOfToday);
  const past = bookings.filter((b) => new Date(b.startsAt).getTime() < startOfToday).reverse();
  return { upcoming, past };
}

// Why cancelling a ticket was refused, as a message to show.
export function cancellationErrorKey(err: HttpErrorResponse): string {
  if (err.status === 409) {
    return problemOf(err)?.code === 'CANCELLATION_CLOSED' ? 'bookings.cancellationClosed' : 'bookings.notCancellable';
  }
  return err.status === 502 ? 'payment.providerDown' : errorMessageKey(err);
}

// The logged-in person's bookings: a paid one is a ticket with its QR code; an unpaid one links to paying. A paid
// ticket can be cancelled (and refunded) until its event's cancellation delay.
@Component({
  selector: 'app-my-bookings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, NgTemplateOutlet, RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, TicketQr],
  templateUrl: './my-bookings.html',
  styleUrl: './my-bookings.css',
})
export class MyBookings {
  private api = inject(RegistrationsService);
  private dialog = inject(MatDialog);
  readonly lang = inject(LanguageService).active;
  readonly cancelling = signal<number | null>(null);
  readonly cancelError = signal<string | null>(null);

  readonly bookings = rxResource({ stream: () => this.api.listMyBookings() });
  readonly split = computed(() => splitByDate(this.bookings.value() ?? [], new Date()));

  cancel(booking: MyBooking): void {
    const data: ConfirmDialogData = {
      title: 'bookings.cancelTitle', message: 'bookings.cancelConfirm', confirm: 'bookings.cancel', cancel: 'bookings.keep',
      params: { event: booking.eventTitle },
    };
    this.dialog.open(ConfirmDialog, { data }).afterClosed().pipe(
      filter((confirmed) => confirmed === true),
      switchMap(() => {
        this.cancelling.set(booking.id);
        this.cancelError.set(null);
        return this.api.cancelMyBooking(booking.id);
      }),
    ).subscribe({
      next: () => {
        this.cancelling.set(null);
        this.bookings.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(null);
        this.cancelError.set(cancellationErrorKey(err));
      },
    });
  }

  errorKey(error: unknown): string {
    return errorMessageKey(error);
  }
}
