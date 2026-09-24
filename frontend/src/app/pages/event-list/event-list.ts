import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { EventFeedItem, PublicService } from '../../api/generated';

@Component({
  selector: 'app-event-list',
  imports: [RouterLink, DatePipe],
  templateUrl: './event-list.html',
})
export class EventList {
  private route = inject(ActivatedRoute);
  private api = inject(PublicService);

  // Read the :slug segment from the route.
  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';

  // Three explicit UI states, driven by signals.
  readonly events = signal<EventFeedItem[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.events() === null && this.error() === null);

  constructor() {
    this.api.listAsblEvents(this.slug).subscribe({
      next: (list) => this.events.set(list),
      error: (err) => this.error.set(`Could not load events (HTTP ${err.status})`),
    });
  }
}
