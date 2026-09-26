import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventManagementService } from '../../api/generated';
import { errorMessageKey, problemOf } from '../../services/problem';

// The event form, for a new event or (with an :id in the route) to edit an existing one.

@Component({
  selector: 'app-create-event',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './create-event.html',
  styles: '.narrow { max-width: 560px; margin: 0 auto; }',
})
export class CreateEvent {
  private api = inject(EventManagementService);
  private router = inject(Router);
  private fb = inject(NonNullableFormBuilder);
  private params = inject(ActivatedRoute).snapshot.paramMap;
  readonly slug = this.params.get('slug') ?? '';
  readonly eventId = this.params.has('id') ? Number(this.params.get('id')) : null;

  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', Validators.maxLength(5000)],
    startsAt: ['', Validators.required], // local date and time, as typed ("2026-12-01T20:00")
    location: ['', Validators.maxLength(255)],
    visibility: ['PUBLIC', Validators.required],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    if (this.eventId !== null) {
      this.api.getManagedEvent(this.slug, this.eventId).subscribe({
        next: (event) => this.form.setValue({
          title: event.title,
          description: event.description ?? '',
          startsAt: localDateTime(event.startsAt),
          location: event.location ?? '',
          visibility: event.visibility,
        }),
        error: (err) => this.error.set(errorMessageKey(err)),
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const event = {
      title: value.title.trim(),
      description: value.description.trim() || undefined,
      // The time the user typed, in their own time zone, turned into an exact moment (UTC) for the API.
      startsAt: new Date(value.startsAt).toISOString(),
      location: value.location.trim() || undefined,
      visibility: value.visibility,
    };
    const request = this.eventId === null
      ? this.api.createEvent(this.slug, event)
      : this.api.updateEvent(this.slug, this.eventId, event);
    request.subscribe({
      next: (saved) => this.router.navigate(['/asbls', this.slug, 'manage', 'events', saved.id]),
      error: (err) => {
        this.submitting.set(false);
        this.error.set(problemOf(err)?.code === 'EVENT_NOT_EDITABLE' ? 'manage.notEditable' : errorMessageKey(err));
      },
    });
  }
}

// An exact moment (from the API) as the local date and time a datetime-local field shows, e.g. "2026-12-01T20:00".
function localDateTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
