import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { errorMessageKey } from '../../services/problem';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  styles: '.narrow { max-width: 420px; margin: 0 auto; }',
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(NonNullableFormBuilder);

  // The form's structure and rules live in the class, not the template: that's what "reactive" means.
  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  readonly submitting = signal(false);
  readonly accountDeleted = this.route.snapshot.queryParamMap.has('deleted');
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // show every field's error, not only the ones the user visited
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      // Back to the page the guard sent us from. navigateByUrl stays inside the app, so a crafted
      // ?returnUrl=https://evil.example can't redirect the user to another site.
      next: () => this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl') ?? '/'),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // 401 gets our own wording: the API deliberately doesn't say whether the email or the password was wrong.
        this.error.set(err.status === 401 ? 'login.error' : errorMessageKey(err));
      },
    });
  }
}
