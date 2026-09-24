import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth';

// For two routes on the same URL, e.g. "/": the dashboard when logged in, the landing page otherwise.
export const whenLoggedIn: CanMatchFn = () => inject(AuthService).isLoggedIn();

// Protects routes that need a logged-in user. This is UX, not security: the API enforces access.
// Logged out -> redirect to /login, remembering where the user wanted to go.
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isLoggedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
