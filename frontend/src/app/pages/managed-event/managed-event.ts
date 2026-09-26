import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormGroupDirective, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, Observable } from 'rxjs';
import { EventManagementService, ManagedEvent, ManagedTicket, RegistrationsService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';

// The lifecycle rules the API may invoke (409 + code), as messages for people.
const LIFECYCLE_ERRORS: Record<string, string> = {
  EVENT_NOT_EDITABLE: 'manage.notEditable',
  SEATS_BELOW_SOLD: 'manage.seatsBelowSold',
  TICKET_IN_USE: 'manage.ticketInUse',
};

// One event in the back office: details, ticket sales, and — for administrators — its lifecycle: ticket categories
// (add, change, remove), publishing, cancelling a published event, deleting a draft. Every change returns the updated
// event, which simply replaces the one on screen. The API enforces every rule; the page only offers what makes sense.
@Component({
  selector: 'app-managed-event',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    RouterLink, DatePipe, CurrencyPipe, ReactiveFormsModule, TranslocoPipe,
    MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, MatTableModule,
  ],
  templateUrl: './managed-event.html',
})
export class ManagedEventPage {
  private api = inject(EventManagementService);
  private fb = inject(NonNullableFormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private registrations = inject(RegistrationsService);
  private dialog = inject(MatDialog);
  readonly lang = inject(LanguageService).active;

  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly eventId = Number(this.route.snapshot.paramMap.get('id'));
  readonly event = signal<ManagedEvent | null>(null);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly columns = ['label', 'price', 'sold'];
  // The category being changed in the ticket form, or null when the form adds a new one.
  readonly editing = signal<ManagedTicket | null>(null);

  // Members can book once the event is published; administrators change categories while the event is editable.
  columnsFor(event: ManagedEvent): string[] {
    return [
      ...this.columns,
      ...(event.status === 'PUBLISHED' ? ['book'] : []),
      ...(this.editable(event) ? ['actions'] : []),
    ];
  }

  editable(event: ManagedEvent): boolean {
    return event.canManage && (event.status === 'DRAFT' || event.status === 'PUBLISHED');
  }

  readonly ticketForm = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
    price: ['', [Validators.required, Validators.pattern(/^\d{1,6}([.,]\d{1,2})?$/)]],
    totalSeats: ['', [Validators.required, Validators.pattern(/^[1-9]\d*$/)]],
  });

  constructor() {
    this.api.getManagedEvent(this.slug, this.eventId).subscribe({
      next: (event) => this.event.set(event),
      error: (err: HttpErrorResponse) => this.error.set(err.status === 403 ? 'manage.noAccess' : errorMessageKey(err)),
    });
  }

  book(ticket: ManagedTicket): void {
    this.saving.set(true);
    this.error.set(null);
    this.registrations.bookTicket(this.slug, this.eventId, { ticketCategoryId: ticket.id }).subscribe({
      next: (registration) => this.router.navigate(['/pay', registration.id]),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(problemOf(err)?.code === 'SOLD_OUT' ? 'manage.soldOut' : errorMessageKey(err));
      },
    });
  }

  publish(): void {
    this.save(this.api.publishEvent(this.slug, this.eventId));
  }

  cancelEvent(): void {
    this.confirm({
      title: 'manage.cancelTitle',
      message: 'manage.cancelConfirm',
      confirm: 'manage.cancelEvent',
      cancel: 'manage.keep',
    }).subscribe(() => this.save(this.api.cancelEvent(this.slug, this.eventId)));
  }

  deleteDraft(): void {
    this.confirm({
      title: 'manage.deleteTitle',
      message: 'manage.deleteConfirm',
      confirm: 'manage.delete',
      cancel: 'manage.keep',
    }).subscribe(() => {
      this.saving.set(true);
      this.api.deleteDraftEvent(this.slug, this.eventId).subscribe({
        next: () => this.router.navigate(['/asbls', this.slug, 'manage', 'events']),
        error: (err: HttpErrorResponse) => this.failed(err),
      });
    });
  }

  // Puts a category in the form to change it.
  editTicket(ticket: ManagedTicket): void {
    this.editing.set(ticket);
    this.ticketForm.setValue({
      label: ticket.label,
      price: ticket.price.toFixed(2),
      totalSeats: String(ticket.totalSeats),
    });
  }

  stopEditing(formDirective: FormGroupDirective): void {
    this.editing.set(null);
    formDirective.resetForm();
  }

  removeTicket(ticket: ManagedTicket): void {
    this.confirm({
      title: 'manage.removeTicketTitle',
      message: 'manage.removeTicketConfirm',
      confirm: 'manage.remove',
      cancel: 'manage.keep',
      params: { label: ticket.label },
    }).subscribe(() => this.save(this.api.removeTicketCategory(this.slug, this.eventId, ticket.id)));
  }

  // Takes the form directive, not just the form: after a ticket is added, resetForm() clears the fields AND
  // the "submitted" state. form.reset() alone keeps "submitted", so the emptied required fields would all
  // show their errors straight away (Material shows errors on invalid fields of a submitted form).
  // Adds a category, or saves the one being changed.
  saveTicket(formDirective: FormGroupDirective): void {
    if (this.ticketForm.invalid) {
      this.ticketForm.markAllAsTouched();
      return;
    }
    const value = this.ticketForm.getRawValue();
    const ticket = {
      label: value.label.trim(),
      price: Number(value.price.replace(',', '.')), // accept "12,50" as written in French and Dutch
      totalSeats: Number(value.totalSeats),
    };
    const editing = this.editing();
    this.save(editing
      ? this.api.updateTicketCategory(this.slug, this.eventId, editing.id, ticket)
      : this.api.addTicketCategory(this.slug, this.eventId, ticket), () => this.stopEditing(formDirective));
  }

  private save(request: Observable<ManagedEvent>, done?: () => void): void {
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (event: ManagedEvent) => {
        this.event.set(event);
        this.saving.set(false);
        done?.();
      },
      error: (err: HttpErrorResponse) => this.failed(err),
    });
  }

  private failed(err: HttpErrorResponse): void {
    this.saving.set(false);
    this.error.set(LIFECYCLE_ERRORS[problemOf(err)?.code ?? ''] ?? errorMessageKey(err));
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true));
  }
}
