import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService, rememberAfterVerify } from '../../services/auth';
import { errorMessageKey } from '../../services/problem';

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './register.html',
  styles: '.narrow { max-width: 420px; margin: 0 auto; }',
})
export class Register {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private fb = inject(NonNullableFormBuilder);

  // The same rules as the API (which checks again): name required, a valid email, password 8–100 characters.
  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  // Set once the form is sent: the address the link went to (the same answer whether or not it has an account).
  readonly sentTo = signal<string | null>(null);
  readonly resent = signal(false);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { name, email, password } = this.form.getRawValue();
    // Where the visitor was going (e.g. an invitation link), for after they've confirmed their email.
    rememberAfterVerify(this.route.snapshot.queryParamMap.get('returnUrl'));
    this.auth.register(name.trim(), email.trim(), password).subscribe({
      next: () => this.sentTo.set(email.trim()),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(errorMessageKey(err));
      },
    });
  }

  resend(): void {
    const email = this.sentTo();
    if (email) {
      this.auth.resendVerification(email).subscribe({ next: () => this.resent.set(true) });
    }
  }
}
