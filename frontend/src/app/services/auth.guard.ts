import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth';

// Both wait for the start-up session restore (instant for visitors without a session), so a returning user
// isn't sent to the landing page or the login form while they're being logged back in.

// For two routes on the same URL, e.g. "/": the dashboard when logged in, the landing page otherwise.
export const whenLoggedIn: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.whenRestored().pipe(map(() => auth.isLoggedIn()));
};

// Protects routes that need a logged-in user. This is UX, not security: the API enforces access.
// Logged out -> redirect to /login, remembering where the user wanted to go.
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .whenRestored()
    .pipe(map(() => auth.isLoggedIn() || router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })));
};
