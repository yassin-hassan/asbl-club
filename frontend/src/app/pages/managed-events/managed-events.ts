import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventManagementService, ManagedEventItem, ManagedEventList } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// An association's events: every one, by section, for those who follow the activity (administrators, treasurers,
// readers); the published ones still to come for plain members (the API decides). Administrators can create more.
export type SectionKey = 'upcoming' | 'drafts' | 'past' | 'cancelled';

// Events by section, empty sections left out: still to come (soonest first), drafts, past (most recent first),
// cancelled (most recent first).
export function sectionsOf(events: ManagedEventItem[], now: Date): { key: SectionKey; events: ManagedEventItem[] }[] {
  const at = (e: ManagedEventItem) => new Date(e.startsAt).getTime();
  const isPast = (e: ManagedEventItem) => at(e) <= now.getTime();
  const soonest = (a: ManagedEventItem, b: ManagedEventItem) => at(a) - at(b);
  const latest = (a: ManagedEventItem, b: ManagedEventItem) => at(b) - at(a);
  const live = events.filter((e) => e.status === 'PUBLISHED' || e.status === 'ENDED');
  const sections: { key: SectionKey; events: ManagedEventItem[] }[] = [
    { key: 'upcoming', events: live.filter((e) => !isPast(e)).sort(soonest) },
    { key: 'drafts', events: events.filter((e) => e.status === 'DRAFT').sort(soonest) },
    { key: 'past', events: live.filter(isPast).sort(latest) },
    { key: 'cancelled', events: events.filter((e) => e.status === 'CANCELLED').sort(latest) },
  ];
  return sections.filter((s) => s.events.length > 0);
}

@Component({
  selector: 'app-managed-events',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, TranslocoPipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './managed-events.html',
})
export class ManagedEvents {
  readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';
  readonly lang = inject(LanguageService).active;
  readonly list = signal<ManagedEventList | null>(null);
  readonly sections = computed(() => sectionsOf(this.list()?.events ?? [], new Date()));
  readonly error = signal<string | null>(null);

  constructor() {
    inject(EventManagementService).listManagedEvents(this.slug).subscribe({
      next: (list) => this.list.set(list),
      error: (err: HttpErrorResponse) => this.error.set(err.status === 403 ? 'manage.noAccess' : errorMessageKey(err)),
    });
  }
}
