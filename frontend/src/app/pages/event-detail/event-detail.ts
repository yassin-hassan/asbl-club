import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';
import { EventFeedItem, PublicService, SeatAvailability } from '../../api/generated';

@Component({
  selector: 'app-event-detail',
  imports: [RouterLink, DatePipe],
  templateUrl: './event-detail.html',
})
export class EventDetail {
  private route = inject(ActivatedRoute);
  private api = inject(PublicService);

  readonly eventId = Number(this.route.snapshot.paramMap.get('id'));

  readonly event = signal<EventFeedItem | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.event() === null && this.error() === null);

  // The RxJS fundamental worth learning: poll availability every 5s.
  // switchMap cancels any in-flight request when the next tick fires, so a
  // slow response can never land after a newer one. startWith(0) fires immediately.
  // toSignal() bridges the Observable into a signal the template can read.
  readonly availability = toSignal(
    interval(5000).pipe(
      startWith(0),
      switchMap(() =>
        this.api
          .getEventAvailability(this.eventId)
          .pipe(catchError(() => of([] as SeatAvailability[]))),
      ),
    ),
    { initialValue: [] as SeatAvailability[] },
  );

  constructor() {
    this.api.getEvent(this.eventId).subscribe({
      next: (e) => this.event.set(e),
      error: (err) => this.error.set(`Could not load event (HTTP ${err.status})`),
    });
  }
}
