import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EventFeedItem, PublicService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';
import { LanguageService } from '../../i18n/language';

@Component({
  selector: 'app-event-list',
  imports: [RouterLink, DatePipe, TranslocoPipe, MatButtonModule, MatIconModule, MatListModule, MatProgressSpinnerModule],
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
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.events() === null && this.error() === null);

  constructor() {
    this.api.listAsblEvents(this.slug).subscribe({
      next: (list) => this.events.set(list),
      error: (err) => this.error.set(errorMessageKey(err, 'events.loadError')),
    });
  }
}
