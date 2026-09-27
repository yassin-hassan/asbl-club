import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthenticationService } from '../../api/generated';
import { errorMessageKey, problemOf } from '../../services/problem';

// Reads the one-time token from the link's fragment ("#token=…": never sent to any server), keeps it in memory, and
// removes it from the address bar at once (no copy left in the history, in a screenshot or a shared URL).
export function tokenFromFragment(fragment: string | null): string | null {
  return new URLSearchParams(fragment ?? '').get('token');
}

function sameAsPassword(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('repeat')?.value ? null : { mismatch: true };
}

// The page the emailed link opens: choose a new password (twice, since a typo here would lock the person out).
@Component({
  selector: 'app-reset-password',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  private api = inject(AuthenticationService);
  private readonly token = tokenFromFragment(inject(ActivatedRoute).snapshot.fragment);

  readonly form = inject(NonNullableFormBuilder).group(
    {
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      repeat: ['', Validators.required],
    },
    { validators: sameAsPassword },
  );
  readonly missingToken = this.token === null;
  readonly submitting = signal(false);
  readonly done = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    inject(Location).replaceState('/reset-password');
  }

  submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.resetPassword({ token: this.token, password: this.form.getRawValue().password }).subscribe({
      next: () => this.done.set(true),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(problemOf(err)?.code === 'INVALID_RESET_TOKEN' ? 'reset.invalidLink' : errorMessageKey(err));
      },
    });
  }
}
