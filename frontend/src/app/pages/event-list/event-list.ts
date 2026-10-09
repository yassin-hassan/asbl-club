import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EventFeedItem, PublicService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';
import { LanguageService } from '../../i18n/language';

@Component({
  selector: 'app-event-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, TranslocoPipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './event-list.html',
})
export class EventList {
  private route = inject(ActivatedRoute);
  private api = inject(PublicService);
  readonly lang = inject(LanguageService).active;

  // Read the :slug segment from the route.
  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';

  // Three explicit UI states, driven by signals.
  readonly events = signal<EventFeedItem[] | null>(null);
  // The association's name for the title (its URL identifier until it arrives).
  readonly name = signal<string>(this.slug);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.events() === null && this.error() === null);

  constructor() {
    this.api.getAsbl(this.slug).subscribe({ next: (asbl) => this.name.set(asbl.denomination), error: () => {} });
    this.api.listAsblEvents(this.slug).subscribe({
      next: (list) => this.events.set(list),
      error: (err) => this.error.set(errorMessageKey(err, 'events.loadError')),
    });
  }

  isPast(startsAt: string): boolean {
    return new Date(startsAt).getTime() <= Date.now();
  }
}
