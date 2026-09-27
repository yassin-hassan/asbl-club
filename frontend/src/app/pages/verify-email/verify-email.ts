import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService, takeAfterVerify } from '../../services/auth';
import { errorMessageKey, problemOf } from '../../services/problem';
import { tokenFromFragment } from '../reset-password/reset-password';

// The page the confirmation email links to: reads the token from the link's fragment (never sent to a server),
// removes it from the address bar at once, confirms the address and logs in, then goes where the person was heading.
@Component({
  selector: 'app-verify-email',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslocoPipe, MatProgressSpinnerModule],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1>{{ 'verify.title' | transloco }}</h1>
        @if (error(); as error) {
          <p class="error" role="alert">{{ error | transloco }}</p>
          <p>{{ 'verify.whatNow' | transloco }} <a routerLink="/login">{{ 'login.submit' | transloco }}</a></p>
        } @else {
          <mat-spinner diameter="40" />
          <p role="status">{{ 'verify.checking' | transloco }}</p>
        }
      </div>
    </div>
  `,
})
export class VerifyEmail {
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly error = signal<string | null>(null);

  constructor() {
    const token = tokenFromFragment(inject(ActivatedRoute).snapshot.fragment);
    inject(Location).replaceState('/verify-email');
    if (!token) {
      this.error.set('verify.invalidLink');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => this.router.navigateByUrl(takeAfterVerify()),
      error: (err: HttpErrorResponse) =>
        this.error.set(problemOf(err)?.code === 'INVALID_VERIFICATION_TOKEN' ? 'verify.invalidLink' : errorMessageKey(err)),
    });
  }
}
