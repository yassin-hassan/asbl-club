import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthenticationService } from '../../api/generated';
import { errorMessageKey } from '../../services/problem';

// "Forgot password": ask for a link by email. The answer never says whether the address has an account (the API
// answers the same either way), so this page can't be used to find out who is registered.
@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private api = inject(AuthenticationService);
  readonly form = inject(NonNullableFormBuilder).group({ email: ['', [Validators.required, Validators.email]] });
  readonly submitting = signal(false);
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.requestPasswordReset({ email: this.form.getRawValue().email.trim() }).subscribe({
      next: () => this.sent.set(true),
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }
}
