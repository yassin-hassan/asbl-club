import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { AssociationsService } from '../../api/generated';
import { errorMessageKey, problemOf } from '../../services/problem';
import { LANGUAGES } from '../../i18n/language';

@Component({
  selector: 'app-create-asbl',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  templateUrl: './create-asbl.html',
  styles: '.narrow { max-width: 520px; margin: 0 auto; }',
})
export class CreateAsbl {
  private api = inject(AssociationsService);
  private router = inject(Router);
  private fb = inject(NonNullableFormBuilder);

  readonly languages = LANGUAGES;

  // The same rules as the API (which checks again) and the database.
  readonly form = this.fb.group({
    denomination: ['', [Validators.required, Validators.maxLength(255)]],
    bceNumber: ['', [Validators.required, Validators.pattern(/^\d{4}\.\d{3}\.\d{3}$/)]],
    slug: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/^[a-z0-9-]+$/)]],
    defaultLanguage: ['fr', Validators.required],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Suggest the URL identifier from the name ("Club Démo" → "club-demo") until the user edits it themselves.
    this.form.controls.denomination.valueChanges.pipe(takeUntilDestroyed()).subscribe((name) => {
      if (!this.form.controls.slug.dirty) {
        this.form.controls.slug.setValue(slugify(name));
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const request = this.form.getRawValue();
    this.api.createAsbl({ ...request, denomination: request.denomination.trim() }).subscribe({
      next: (asbl) => this.router.navigate(['/asbls', asbl.slug, 'members']),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const taken = err.status === 409 ? problemOf(err)?.errors : undefined;
        if (taken?.['slug']) {
          this.form.controls.slug.setErrors({ taken: true });
        } else if (taken?.['bceNumber']) {
          this.form.controls.bceNumber.setErrors({ taken: true });
        } else {
          this.error.set(errorMessageKey(err));
        }
      },
    });
  }
}

function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '') // drop accents
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
