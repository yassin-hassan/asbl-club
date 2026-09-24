import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventManagementService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

@Component({
  selector: 'app-create-event',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './create-event.html',
  styles: '.narrow { max-width: 560px; margin: 0 auto; }',
})
export class CreateEvent {
  private api = inject(EventManagementService);
  private router = inject(Router);
  private fb = inject(NonNullableFormBuilder);
  readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';

  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', Validators.maxLength(5000)],
    startsAt: ['', Validators.required], // local date and time, as typed ("2026-12-01T20:00")
    location: ['', Validators.maxLength(255)],
    visibility: ['PUBLIC', Validators.required],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.api.createEvent(this.slug, {
      title: value.title.trim(),
      description: value.description.trim() || undefined,
      // The time the user typed, in their own time zone, turned into an exact moment (UTC) for the API.
      startsAt: new Date(value.startsAt).toISOString(),
      location: value.location.trim() || undefined,
      visibility: value.visibility,
    }).subscribe({
      next: (event) => this.router.navigate(['/asbls', this.slug, 'manage', 'events', event.id]),
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }
}
