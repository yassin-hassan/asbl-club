import { Component, computed, inject, input, signal, ChangeDetectionStrategy, DOCUMENT } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { Attendee, EventManagementService, ManagedEvent } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

const PAID = new Set(['PAID', 'CONFIRMED', 'ATTENDED']);

// Who booked the event, for administrators and treasurers (the API refuses everyone else). A resource: it loads when
// the event is shown and loads again whenever the page's event changes (a ticket edited, the event cancelled…).
@Component({
  selector: 'app-attendee-list',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CurrencyPipe, DatePipe, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './attendee-list.html',
})
export class AttendeeList {
  private api = inject(EventManagementService);
  private document = inject(DOCUMENT);
  readonly lang = inject(LanguageService).active;

  readonly slug = input.required<string>();
  readonly event = input.required<ManagedEvent>();

  readonly attendees = rxResource({
    params: () => ({ slug: this.slug(), event: this.event() }),
    stream: ({ params }) => this.api.listAttendees(params.slug, params.event.id),
  });
  readonly paid = computed(() => this.count((a) => PAID.has(a.status)));
  readonly waiting = computed(() => this.count((a) => a.status === 'RESERVED'));
  readonly columns = ['name', 'email', 'ticket', 'status', 'amount', 'bookedAt'];

  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);

  errorKey(error: unknown): string {
    return errorMessageKey(error);
  }

  // The file comes through the API client (it needs the access token), then is handed to the browser to save.
  // Accept is set explicitly: the contract lists text/csv and, for errors, application/problem+json, and the
  // generated client would otherwise pick the JSON one, asking for an error format only (the server answers 406).
  download(): void {
    const slug = this.slug();
    const eventId = this.event().id;
    this.downloading.set(true);
    this.downloadError.set(null);
    this.api.exportAttendees(slug, eventId, 'body', false, { httpHeaderAccept: 'text/csv' }).subscribe({
      next: (csv) => {
        const url = URL.createObjectURL(csv);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = `attendees-${slug}-${eventId}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: (err) => {
        this.downloading.set(false);
        this.downloadError.set(errorMessageKey(err));
      },
    });
  }

  private count(matches: (attendee: Attendee) => boolean): number {
    return this.attendees.value()?.attendees.filter(matches).length ?? 0;
  }
}
