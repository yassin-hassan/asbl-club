import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe, DOCUMENT } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, interval, of, switchMap } from 'rxjs';
import { PublicEvent, PublicService, PublicTicket, SeatAvailability } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';
import { LanguageService } from '../../i18n/language';

@Component({
  selector: 'app-event-detail',
  imports: [
    RouterLink, DatePipe, CurrencyPipe, TranslocoPipe,
    MatButtonModule, MatProgressSpinnerModule, MatTableModule,
  ],
  templateUrl: './event-detail.html',
})
export class EventDetail {
  private route = inject(ActivatedRoute);
  private api = inject(PublicService);
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;

  readonly eventId = Number(this.route.snapshot.paramMap.get('id'));

  readonly event = signal<PublicEvent | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.event() === null && this.error() === null);

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
  readonly columns = ['label', 'price', 'remaining'];

  // Share links point at this page; the text is encoded so titles with spaces or "&" survive.
  readonly shareUrl = computed(() => this.document.location.href);
  readonly whatsAppLink = computed(
    () => `https://wa.me/?text=${encodeURIComponent(`${this.event()?.title ?? ''} ${this.shareUrl()}`)}`,
  );
  readonly emailLink = computed(
    () => `mailto:?subject=${encodeURIComponent(this.event()?.title ?? '')}&body=${encodeURIComponent(this.shareUrl())}`,
  );

  constructor() {
    this.api.getEvent(this.eventId).subscribe({
      next: (e) => this.event.set(e),
      error: (err) => this.error.set(errorMessageKey(err, 'event.loadError')),
    });
  }
}
