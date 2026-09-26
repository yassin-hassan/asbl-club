import { afterNextRender, Component, ElementRef, inject, signal, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventManagementService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

type Scan =
  | { kind: 'in'; name: string; ticket: string }
  | { kind: 'again'; name: string; ticket: string; at: string }
  | { kind: 'refused'; messageKey: string };

// At the door: type or scan a ticket's code. A handheld barcode scanner behaves like a keyboard (it types the code
// and presses Enter), so one field that keeps the focus serves both. The server decides; the page shows the answer,
// big and coloured, and keeps a short history of the last scans.
@Component({
  selector: 'app-check-in',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './check-in.html',
  styleUrl: './check-in.css',
})
export class CheckInPage {
  private api = inject(EventManagementService);
  private params = inject(ActivatedRoute).snapshot.paramMap;
  readonly lang = inject(LanguageService).active;
  readonly slug = this.params.get('slug') ?? '';
  readonly eventId = Number(this.params.get('id'));

  readonly form = inject(NonNullableFormBuilder).group({ code: ['', Validators.required] });
  readonly scans = signal<Scan[]>([]);
  readonly checking = signal(false);
  private field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  constructor() {
    afterNextRender(() => this.field().nativeElement.focus());
  }

  submit(): void {
    const code = this.form.getRawValue().code.trim();
    if (!code || this.checking()) {
      return;
    }
    this.checking.set(true);
    this.api.checkIn(this.slug, this.eventId, { code }).subscribe({
      next: (result) => this.show(result.outcome === 'CHECKED_IN'
        ? { kind: 'in', name: result.name, ticket: result.ticketLabel }
        : { kind: 'again', name: result.name, ticket: result.ticketLabel, at: result.checkedInAt }),
      error: (err: HttpErrorResponse) => this.show({ kind: 'refused', messageKey: refusal(err) }),
    });
  }

  private show(scan: Scan): void {
    this.scans.update((scans) => [scan, ...scans].slice(0, 8));
    this.checking.set(false);
    this.form.reset();
    this.field().nativeElement.focus(); // ready for the next person
  }
}

function refusal(err: HttpErrorResponse): string {
  if (err.status === 404) {
    return 'checkIn.unknown';
  }
  switch (problemOf(err)?.code) {
    case 'TICKET_NOT_VALID':
      return 'checkIn.notValid';
    case 'EVENT_NOT_OPEN':
      return 'checkIn.eventNotOpen';
    default:
      return errorMessageKey(err);
  }
}
