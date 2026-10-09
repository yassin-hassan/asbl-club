import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe, DOCUMENT } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, interval, of, switchMap } from 'rxjs';
import {
  GuestBookingsService, PublicEvent, PublicService, PublicTicket, RegistrationsService, SeatAvailability,
} from '../../api/generated';
import { AuthService } from '../../services/auth';
import { errorMessageKey, problemOf } from '../../services/problem';
import { LanguageService } from '../../i18n/language';

@Component({
  selector: 'app-event-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink, ReactiveFormsModule, DatePipe, CurrencyPipe, TranslocoPipe,
    MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, MatTableModule,
  ],
  templateUrl: './event-detail.html',
})
export class EventDetail {
  private route = inject(ActivatedRoute);
  private api = inject(PublicService);
  private document = inject(DOCUMENT);
  private router = inject(Router);
  private registrations = inject(RegistrationsService);
  private guestBookings = inject(GuestBookingsService);
  readonly auth = inject(AuthService);
  readonly lang = inject(LanguageService).active;

  readonly eventId = Number(this.route.snapshot.paramMap.get('id'));

  readonly event = signal<PublicEvent | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.event() === null && this.error() === null);
  // Started (or over): no more bookings, the page stays as a record.
  readonly over = computed(() => {
    const event = this.event();
    return event !== null && new Date(event.startsAt).getTime() <= Date.now();
  });

  // The event arrives with its tickets and remaining seats; after that, seats are polled every 5 s.
  // switchMap drops a slow response when the next tick fires, so an old count never overwrites a newer one.
  private readonly liveSeats = toSignal(
    interval(5000).pipe(
      switchMap(() =>
        this.api.getEventAvailability(this.eventId).pipe(catchError(() => of(null as SeatAvailability[] | null))),
      ),
    ),
    { initialValue: null },
  );

  // Ticket names and prices from the event, remaining seats from the latest poll when there is one.
  readonly tickets = computed<PublicTicket[]>(() => {
    const tickets = this.event()?.tickets ?? [];
    const live = new Map((this.liveSeats() ?? []).map((seat) => [seat.categoryId, seat.remaining]));
    return tickets.map((ticket) => ({ ...ticket, remaining: live.get(ticket.id) ?? ticket.remaining }));
  });
  readonly columns = ['label', 'price', 'remaining', 'book'];

  // Booking. With an account: straight to payment. Without: a name and an email address are enough (a "guest");
  // the booking is then reached through a secret link, shown next and emailed.
  readonly chosen = signal<PublicTicket | null>(null);
  readonly saving = signal(false);
  readonly bookError = signal<string | null>(null);
  readonly guestForm = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
  });

  // Share links point at this page; the text is encoded so titles with spaces or "&" survive.
  readonly shareUrl = computed(() => this.document.location.href);
  readonly whatsAppLink = computed(
    () => `https://wa.me/?text=${encodeURIComponent(`${this.event()?.title ?? ''} ${this.shareUrl()}`)}`,
  );
  readonly emailLink = computed(
    () => `mailto:?subject=${encodeURIComponent(this.event()?.title ?? '')}&body=${encodeURIComponent(this.shareUrl())}`,
  );

  book(ticket: PublicTicket): void {
    this.bookError.set(null);
    if (!this.auth.user()) {
      this.chosen.set(ticket);
      return;
    }
    this.saving.set(true);
    this.registrations.bookPublicEvent(this.eventId, { ticketCategoryId: ticket.id }).subscribe({
      next: (registration) => this.router.navigate(['/pay', registration.id]),
      error: (err: HttpErrorResponse) => this.failed(err),
    });
  }

  bookAsGuest(): void {
    const ticket = this.chosen();
    if (!ticket || this.guestForm.invalid) {
      this.guestForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.bookError.set(null);
    const { name, email } = this.guestForm.getRawValue();
    this.guestBookings.bookAsGuest({
      eventId: this.eventId, ticketCategoryId: ticket.id, name, email, language: this.lang(),
    }).subscribe({
      next: (booked) => this.router.navigate(['/tickets', booked.accessToken]),
      error: (err: HttpErrorResponse) => this.failed(err),
    });
  }

  cancelGuest(): void {
    this.chosen.set(null);
    this.bookError.set(null);
  }

  private failed(err: HttpErrorResponse): void {
    this.saving.set(false);
    const code = problemOf(err)?.code;
    this.bookError.set(
      code === 'SOLD_OUT' ? 'event.soldOut'
        : code === 'PAYMENTS_DISABLED' ? 'payment.paymentsDisabled'
          : code === 'EVENT_OVER' ? 'event.overError'
            : errorMessageKey(err),
    );
  }

  constructor() {
    this.api.getEvent(this.eventId).subscribe({
      next: (e) => this.event.set(e),
      error: (err) => this.error.set(errorMessageKey(err, 'event.loadError')),
    });
  }
}
