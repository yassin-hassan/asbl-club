import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventManagementService, ManagedEventList } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

// An association's events as its members see them: drafts included. Administrators can create more.
@Component({
  selector: 'app-managed-events',
  imports: [RouterLink, DatePipe, TranslocoPipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './managed-events.html',
})
export class ManagedEvents {
  readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';
  readonly lang = inject(LanguageService).active;
  readonly list = signal<ManagedEventList | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    inject(EventManagementService).listManagedEvents(this.slug).subscribe({
      next: (list) => this.list.set(list),
      error: (err: HttpErrorResponse) => this.error.set(err.status === 403 ? 'manage.noAccess' : errorMessageKey(err)),
    });
  }
}
