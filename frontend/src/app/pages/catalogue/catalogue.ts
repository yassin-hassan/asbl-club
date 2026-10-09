import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { CatalogueEvent, PublicService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// The catalogue (/events): every association's public events still to come, soonest first, for anyone. Each leads
// to the event's public page, where it can be booked, with or without an account.
@Component({
  selector: 'app-catalogue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslocoPipe, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './catalogue.html',
})
export class Catalogue {
  readonly lang = inject(LanguageService).active;

  readonly events = signal<CatalogueEvent[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = computed(() => this.events() === null && this.error() === null);

  constructor() {
    inject(PublicService).listUpcomingEvents().subscribe({
      next: (list) => this.events.set(list),
      error: (err) => this.error.set(errorMessageKey(err, 'catalogue.loadError')),
    });
  }
}
