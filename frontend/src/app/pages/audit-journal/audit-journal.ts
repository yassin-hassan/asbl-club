import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { AuditJournal, AuditService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey } from '../../services/problem';

type State = { journal: AuditJournal } | { error: string } | { loading: true };

// Both audit journals: an association's (route with :slug, its administrators) and the platform's
// (/admin/audit, super-administrators). The API decides who may read; this page shows a message on 403.
// The page number lives in the URL (?page=), so reloading or going back keeps the reader's place.
@Component({
  selector: 'app-audit-journal',
  imports: [DatePipe, RouterLink, TranslocoPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './audit-journal.html',
})
export class AuditJournalPage {
  private readonly api = inject(AuditService);
  private readonly route = inject(ActivatedRoute);
  readonly lang = inject(LanguageService).active;
  readonly slug = this.route.snapshot.paramMap.get('slug');
  readonly platform = this.slug === null;
  readonly columns = this.platform
    ? ['when', 'asbl', 'actor', 'action', 'entity', 'ip']
    : ['when', 'actor', 'action', 'entity'];

  readonly state = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => Math.max(Number(params.get('page')) || 1, 1)),
      switchMap((page) =>
        this.load(page - 1).pipe(
          map((journal): State => ({ journal })),
          catchError((err: HttpErrorResponse) =>
            of<State>({ error: err.status === 403 ? 'audit.adminsOnly' : errorMessageKey(err) }),
          ),
          startWith<State>({ loading: true }),
        ),
      ),
    ),
    { initialValue: { loading: true } as State },
  );

  private load(page: number): Observable<AuditJournal> {
    return this.slug === null
      ? this.api.getPlatformAuditJournal(page)
      : this.api.getAsblAuditJournal(this.slug, page);
  }
}
