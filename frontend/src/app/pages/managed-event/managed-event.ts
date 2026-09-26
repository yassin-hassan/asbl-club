import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormGroupDirective, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';
import { EventManagementService, ManagedEvent, ManagedTicket, RegistrationsService } from '../../api/generated';
import { LanguageService } from '../../i18n/language';
import { errorMessageKey, problemOf } from '../../services/problem';

// One event in the back office: details, ticket sales, and — for administrators — adding ticket categories and
// publishing. Every change returns the updated event, which simply replaces the one on screen.
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
  readonly lang = inject(LanguageService).active;

  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly eventId = Number(this.route.snapshot.paramMap.get('id'));
  readonly event = signal<ManagedEvent | null>(null);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly columns = ['label', 'price', 'sold'];
  // Members can book once the event is published.
  columnsFor(event: ManagedEvent): string[] {
    return event.status === 'PUBLISHED' ? [...this.columns, 'book'] : this.columns;
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

  // Takes the form directive, not just the form: after a ticket is added, resetForm() clears the fields AND
  // the "submitted" state. form.reset() alone keeps "submitted", so the emptied required fields would all
  // show their errors straight away (Material shows errors on invalid fields of a submitted form).
  addTicket(formDirective: FormGroupDirective): void {
    if (this.ticketForm.invalid) {
      this.ticketForm.markAllAsTouched();
      return;
    }
    const value = this.ticketForm.getRawValue();
    this.save(this.api.addTicketCategory(this.slug, this.eventId, {
      label: value.label.trim(),
      price: Number(value.price.replace(',', '.')), // accept "12,50" as written in French and Dutch
      totalSeats: Number(value.totalSeats),
    }), () => formDirective.resetForm());
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
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }
}
