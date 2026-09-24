import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth';

// Runs for every HttpClient request: attaches the access token to our API calls, and when the API
// answers 401 (token expired), refreshes the token once and retries the request.
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.accessToken();
  if (token === null || !needsAccessToken(req.url)) {
    return next(req);
  }
  return next(withToken(req, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refreshAccessToken().pipe(
        catchError((refreshError: unknown) => {
          // The refresh token is gone too (expired, logged out elsewhere, revoked): back to login.
          auth.clearSession();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
        // Retried once, through the rest of the chain only: a second 401 is returned as is, no loop.
        switchMap((newToken) => next(withToken(req, newToken))),
      );
    }),
  );
};

function withToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  // Requests are immutable: clone it with the extra header instead of changing it.
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

// Only our own API (relative /api/ URLs, so never another site), and not the auth endpoints:
// login/logout don't need it, and an expired token sent to /refresh would get a 401 before the
// endpoint even runs.
function needsAccessToken(url: string): boolean {
  return url.startsWith('/api/') && !url.startsWith('/api/v1/auth/');
}
