import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { MyBooking, RegistrationsService } from '../../api/generated';
import { TicketQr } from '../../components/ticket-qr/ticket-qr';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// Splits bookings into events still to come and events that are over (an event counts as upcoming the whole day
// it happens, so a ticket doesn't disappear at the door). Soonest upcoming first, most recent past first.
export function splitByDate(bookings: MyBooking[], now: Date): { upcoming: MyBooking[]; past: MyBooking[] } {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const upcoming = bookings.filter((b) => new Date(b.startsAt).getTime() >= startOfToday);
  const past = bookings.filter((b) => new Date(b.startsAt).getTime() < startOfToday).reverse();
  return { upcoming, past };
}

// The logged-in person's bookings: a paid one is a ticket with its QR code; an unpaid one links to paying.
@Component({
  selector: 'app-my-bookings',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CurrencyPipe, DatePipe, NgTemplateOutlet, RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, TicketQr],
  templateUrl: './my-bookings.html',
  styleUrl: './my-bookings.css',
})
export class MyBookings {
  private api = inject(RegistrationsService);
  readonly lang = inject(LanguageService).active;

  readonly bookings = rxResource({ stream: () => this.api.listMyBookings() });
  readonly split = computed(() => splitByDate(this.bookings.value() ?? [], new Date()));

  errorKey(error: unknown): string {
    return errorMessageKey(error);
  }
}
